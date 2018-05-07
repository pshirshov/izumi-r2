package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.IncompatibleTypesException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.SetStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningContext, op: SetOp.CreateSet): Seq[OpResult.NewInstance] = {
    import op._
    // target is guaranteed to be a Set
    val scalaCollectionSetType = RuntimeDIUniverse.SafeType.get[collection.Set[_]]
    val erasure = scalaCollectionSetType.tpe.typeSymbol

    if (!tpe.tpe.baseClasses.contains(erasure)) {
      throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
        s"For $target expected $tpe to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , tpe)
    }

    val newSet = op.members.map(m => context.fetchKey(m).get)

    Seq(OpResult.NewInstance(target, newSet))
  }

//  def addToSet(context: ProvisioningContext, op: SetOp.AddToSet): Seq[OpResult.UpdatedSet] = {
//    Seq()
////    // value is guaranteed to have already been instantiated or imported
////    val targetElement = context.fetchKey(op.element) match {
////      case Some(value) =>
////        value
////      case _ =>
////        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
////          s" but the instance has not been initialized! Set: ${op.target}, instance: ${op.element}")
////    }
////
////    // set is guaranteed to have already been added
////    val targetSet = context.fetchKey(op.target) match {
////      case Some(set: Set[_]) =>
////        set.asInstanceOf[Set[Any]]
////      case Some(somethingElse) =>
////        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
////          s" but target Set is not a Set! It's ${somethingElse.getClass.getName}")
////
////      case _ =>
////        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
////          s" but Set has not been initialized! Set: ${op.target}, instance: ${op.element}")
////    }
////
////    if (targetSet == targetElement) {
////      throw new DIException(s"Pathological case. Tried to add set into itself: $targetSet", null)
////    }
////
////    Seq(OpResult.UpdatedSet(op.target, targetSet + targetElement))
//  }
}


