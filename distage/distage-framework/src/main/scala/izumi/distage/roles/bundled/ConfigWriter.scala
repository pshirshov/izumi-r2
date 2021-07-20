package izumi.distage.roles.bundled

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import izumi.distage.config.model.ConfTag
import izumi.distage.framework.services.RoleAppPlanner
import izumi.distage.model.definition.Id
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{DIPlan, ExecutableOp}
import izumi.distage.roles.bundled.ConfigWriter.{ConfigPath, ConfigurableComponent, ExtractConfigPath, WriteReference}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.Value
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.{ParserDef, RoleParserSchema}
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.LogstageModule

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.annotation.nowarn
import scala.collection.compat.immutable.ArraySeq
import scala.util.Try

final class ConfigWriter[F[_]](
  logger: IzLogger,
  launcherVersion: ArtifactVersion @Id("launcher-version"),
  roleInfo: RolesInfo,
  roleAppPlanner: RoleAppPlanner,
  F: QuasiIO[F],
) extends RoleTask[F] {

  // fixme: always include `activation` section in configs (Used in RoleAppLauncherImpl#configActivationSection, but not seen in config bindings, since it's not read by DI)
  //  should've been unnecessary after https://github.com/7mind/izumi/issues/779
  //  but, the contents of the MainAppModule (including `"activation"` config read) are not accessible here from `RoleAppPlanner` yet...
  private[this] val _HackyMandatorySection = ConfigPath("activation")

  override def start(roleParameters: RawEntrypointParams, @unused freeArgs: Vector[String]): F[Unit] = {
    F.maybeSuspend {
      val config = ConfigWriter.parse(roleParameters)
      writeReferenceConfig(config)
    }
  }

  private[this] def writeReferenceConfig(options: WriteReference): Unit = {
    val configPath = Paths.get(options.targetDir).toFile
    logger.info(s"Config ${configPath.getAbsolutePath -> "directory to use"}...")

    if (!configPath.exists()) {
      configPath.mkdir()
    }

    logger.info(s"Going to process ${roleInfo.availableRoleBindings.size -> "roles"}")

    val commonComponent = ConfigurableComponent("common", Some(launcherVersion), None)
    val commonConfig = buildConfig(options, commonComponent)

    if (!options.includeCommon) {
      writeConfig(options, commonComponent, None, commonConfig)
    }

    roleInfo.availableRoleBindings.foreach {
      role =>
        val component = ConfigurableComponent(role.descriptor.id, role.descriptor.artifact.map(_.version), Some(commonConfig))
        val refConfig = buildConfig(options, component)
        val versionedComponent = if (options.useLauncherVersion) {
          component.copy(version = Some(ArtifactVersion(launcherVersion.version)))
        } else {
          component
        }

        try {
          writeConfig(options, versionedComponent, None, refConfig)
          minimizedConfig(refConfig, role)
            .foreach {
              cfg =>
                writeConfig(options, versionedComponent, Some("minimized"), cfg)
            }
        } catch {
          case exception: Throwable =>
            logger.crit(s"Cannot process role ${role.descriptor.id}")
            throw exception
        }
    }
  }

  private[this] def buildConfig(config: WriteReference, cmp: ConfigurableComponent): Config = {
    val referenceConfig = s"${cmp.roleId}-reference.conf"
    logger.info(s"[${cmp.roleId}] Resolving $referenceConfig... with ${config.includeCommon -> "shared sections"}")

    val reference = Value(ConfigFactory.parseResourcesAnySyntax(referenceConfig))
      .mut(cmp.parent.filter(_ => config.includeCommon))(_.withFallback(_))
      .get
      .resolve()

    if (reference.isEmpty) {
      logger.warn(s"[${cmp.roleId}] Reference config is empty.")
    }

    val resolved = ConfigFactory
      .systemProperties()
      .withFallback(reference)
      .resolve()

    val filtered = cleanupEffectiveAppConfig(resolved, reference)
    filtered.checkValid(reference)
    filtered
  }

  private[this] def minimizedConfig(config: Config, role: RoleBinding): Option[Config] = {
    val roleDIKey = role.binding.key

    val bootstrapOverride = new LogstageModule(LogRouter.nullRouter, setupStaticLogRouter = false)

    val plans = roleAppPlanner
      .reboot(bootstrapOverride)
      .makePlan(Set(roleDIKey))

    def getConfig(plan: DIPlan): Iterator[ConfigPath] = {
      plan.steps.iterator.collect {
        case ExtractConfigPath(path) => path
      }
    }

    val resolvedConfig =
      (getConfig(plans.app.primary) ++
      getConfig(plans.app.side) ++
      getConfig(plans.app.shared)).toSet + _HackyMandatorySection

    if (plans.app.primary.steps.exists(_.target == roleDIKey)) {
      Some(ConfigWriter.minimized(resolvedConfig, config))
    } else {
      logger.warn(s"$roleDIKey is not in the refined plan")
      None
    }
  }

  private[this] def writeConfig(config: WriteReference, cmp: ConfigurableComponent, suffix: Option[String], typesafeConfig: Config): Try[Unit] = {
    val fileName = outputFileName(cmp.roleId, cmp.version, config.asJson, suffix)
    val target = Paths.get(config.targetDir, fileName)

    Try {
      val cfg = typesafeConfig.root().render(configRenderOptions.setJson(config.asJson))
      val bytes = cfg.getBytes(StandardCharsets.UTF_8)
      Files.write(target, bytes)
      logger.info(s"[${cmp.roleId}] Reference config saved -> $target (${bytes.size} bytes)")
    }.recover {
      case error: Throwable =>
        logger.error(s"[${cmp.roleId -> "component id" -> null}] Can't write reference config to $target, $error")
    }
  }

  // TODO: sdk?
  @nowarn("msg=Unused import")
  private[this] def cleanupEffectiveAppConfig(effectiveAppConfig: Config, reference: Config): Config = {
    import scala.collection.compat._
    import scala.jdk.CollectionConverters._

    ConfigFactory.parseMap(effectiveAppConfig.root().unwrapped().asScala.view.filterKeys(reference.hasPath).toMap.asJava)
  }

  private[this] def outputFileName(service: String, version: Option[ArtifactVersion], asJson: Boolean, suffix: Option[String]): String = {
    val extension = if (asJson) "json" else "conf"
    val vstr = version.map(_.version).getOrElse("0.0.0-UNKNOWN")
    val suffixStr = suffix.fold("")("-" + _)

    s"$service$suffixStr-$vstr.$extension"
  }

  private[this] final val configRenderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setComments(false)
}

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"

  override def parserSchema: RoleParserSchema = {
    RoleParserSchema(id, Options, Some("Dump reference configs for all the roles"), None, freeArgsAllowed = false)
  }

  /**
    * Configuration for [[ConfigWriter]]
    *
    * @param includeCommon Append shared sections from `common-reference.conf` into every written config
    */
  case class WriteReference(
    asJson: Boolean,
    targetDir: String,
    includeCommon: Boolean,
    useLauncherVersion: Boolean,
  )

  final case class ConfigurableComponent(
    roleId: String,
    version: Option[ArtifactVersion],
    parent: Option[Config],
  )

  object Options extends ParserDef {
    final val targetDir = arg("target", "t", "target directory", "<path>")
    final val excludeCommon = flag("exclude-common", "ec", "do not include shared sections")
    final val useComponentVersion = flag("version-use-component", "vc", "use component version instead of launcher version")
    final val formatTypesafe = arg("format", "f", "output format, json is default", "{json|hocon}")
  }

  def parse(p: RawEntrypointParams): WriteReference = {
    val targetDir = p.findValue(Options.targetDir).map(_.value).getOrElse("config")
    val includeCommon = p.hasNoFlag(Options.excludeCommon)
    val useLauncherVersion = p.hasNoFlag(Options.useComponentVersion)
    val asJson = !p.findValue(Options.formatTypesafe).map(_.value).contains("hocon")

    WriteReference(
      asJson,
      targetDir,
      includeCommon,
      useLauncherVersion,
    )
  }

  @nowarn("msg=Unused import")
  def minimized(requiredPaths: Set[ConfigPath], source: Config): Config = {
    import scala.collection.compat._
    import scala.jdk.CollectionConverters._

    val paths = requiredPaths.map(_.toPath)

    ConfigFactory.parseMap {
      source
        .root().unwrapped().asScala
        .view
        .filterKeys(key => paths.exists(_.startsWith(key)))
        .toMap
        .asJava
    }
  }

  object ExtractConfigPath {
    def unapply(op: ExecutableOp): Option[ConfigPath] = {
      op.origin.value match {
        case defined: OperationOrigin.Defined =>
          defined.binding.tags.collectFirst {
            case ConfTag(path) => ConfigPath(path)
          }
        case _ =>
          None
      }
    }
  }

  final case class ConfigPath(parts: Seq[String]) {
    def toPath: String = parts.mkString(".")
  }
  object ConfigPath {
    def apply(path: String): ConfigPath = new ConfigPath(ArraySeq.unsafeWrapArray(path.split('.')))
  }

}
