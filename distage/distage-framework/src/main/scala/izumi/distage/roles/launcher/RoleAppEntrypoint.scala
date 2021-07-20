package izumi.distage.roles.launcher

import distage.TagK
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax._
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{AbstractRole, RoleService, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger

trait RoleAppEntrypoint[F[_]] {
  def runTasksAndRoles(locator: Locator, effect: QuasiIO[F]): F[Unit]
}

object RoleAppEntrypoint {
  class Impl[F[_]: TagK](
    roles: RolesInfo,
    lateLogger: IzLogger,
    parameters: RawAppArgs,
    hook: AppShutdownStrategy[F],
  ) extends RoleAppEntrypoint[F] {

    override def runTasksAndRoles(locator: Locator, effect: QuasiIO[F]): F[Unit] = {
      implicit val F: QuasiIO[F] = effect
      val roleIndex = getRoleIndex(locator)
      for {
        _ <- runTasks(roleIndex)
        _ <- runRoles(roleIndex)
      } yield ()
    }

    protected def runRoles(index: Map[String, AbstractRole[F]])(implicit F: QuasiIO[F]): F[Unit] = {
      val rolesToRun = parameters.roles.flatMap {
        r =>
          index.get(r.role) match {
            case Some(_: RoleTask[F]) =>
              Seq.empty
            case Some(value: RoleService[F]) =>
              Seq(value -> r)
            case Some(v) =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} has unexpected type: $v")
            case None =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
          }
      }

      if (rolesToRun.nonEmpty) {
        lateLogger.info(s"Going to run: ${rolesToRun.size -> "roles"}")

        val roleServices = rolesToRun.map {
          case (task, cfg) =>
            task -> task.start(cfg.roleParameters, cfg.freeArgs)
        }

        Lifecycle
          .traverse(roleServices) {
            case (role, resource) =>
              resource
                .wrapAcquire {
                  acquire =>
                    F.suspendF {
                      lateLogger.info(s"Role is about to initialize: $role")
                      acquire.flatMap(a => F.maybeSuspend { lateLogger.info(s"Role initialized: $role"); a })
                    }
                }.catchAll {
                  t =>
                    Lifecycle.liftF {
                      F.suspendF {
                        lateLogger.error(s"Role $role failed: $t")
                        F.fail(t)
                      }
                    }
                }
          }
          .use(_ => hook.awaitShutdown(lateLogger))
      } else {
        F.maybeSuspend(lateLogger.info("No services to run, exiting..."))
      }
    }

    protected def runTasks(index: Map[String, AbstractRole[F]])(implicit F: QuasiIO[F]): F[Unit] = {
      val tasksToRun = parameters.roles.flatMap {
        r =>
          index.get(r.role) match {
            case Some(value: RoleTask[F]) =>
              Seq(value -> r)
            case Some(_: RoleService[F]) =>
              Seq.empty
            case Some(v) =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} has unexpected type: $v")
            case None =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
          }
      }

      lateLogger.info(s"Going to run: ${tasksToRun.size -> "tasks"}")

      F.traverse_(tasksToRun) {
        case (task, cfg) =>
          val loggedTask = for {
            _ <- F.maybeSuspend(lateLogger.info(s"Task is about to start: $task"))
            _ <- task.start(cfg.roleParameters, cfg.freeArgs)
            _ <- F.maybeSuspend(lateLogger.info(s"Task finished: $task"))
          } yield ()

          F.definitelyRecover(loggedTask) {
            error =>
              for {
                _ <- F.maybeSuspend(lateLogger.error(s"Task failed: $task, $error"))
                _ <- F.fail[Unit](error)
              } yield ()
          }
      }
    }

    private def getRoleIndex(rolesLocator: Locator): Map[String, AbstractRole[F]] = {
      roles.availableRoleBindings.flatMap {
        b =>
          rolesLocator.lookupInstance[AbstractRole[F]](b.binding.key) match {
            case Some(value) =>
              Seq(b.descriptor.id -> value)
            case _ =>
              Seq.empty
          }
      }.toMap
    }

  }
}
