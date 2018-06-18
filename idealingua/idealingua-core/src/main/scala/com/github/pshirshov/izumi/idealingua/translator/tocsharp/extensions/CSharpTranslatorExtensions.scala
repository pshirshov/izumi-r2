package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

class CSharpTranslatorExtensions(ctx: CSTContext, extensions: Seq[CSharpTranslatorExtension]) {
  def preModelEmit(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): String = id match {
    case i: Identifier => extensions.map(ex => ex.preModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.preModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case d: DTO => extensions.map(ex => ex.preModelEmit(ctx, d)).filterNot(_.isEmpty).mkString("\n")
    case i: Interface => extensions.map(ex => ex.preModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case a: Adt => extensions.map(ex => ex.preModelEmit(ctx, a)).filterNot(_.isEmpty).mkString("\n")
    case _ => ""
  }

  def postModelEmit(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): String = id match {
    case i: Identifier => extensions.map(ex => ex.postModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.postModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case d: DTO => extensions.map(ex => ex.postModelEmit(ctx, d)).filterNot(_.isEmpty).mkString("\n")
    case i: Interface => extensions.map(ex => ex.postModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case a: Adt => extensions.map(ex => ex.postModelEmit(ctx, a)).filterNot(_.isEmpty).mkString("\n")
    case _ => ""
  }

  def imports(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): Seq[String] = id match {
    case i: Identifier => extensions.flatMap(ex => ex.imports(ctx, i))
    case e: Enumeration => extensions.flatMap(ex => ex.imports(ctx, e))
    case d: DTO => extensions.flatMap(ex => ex.imports(ctx, d))
    case i: Interface => extensions.flatMap(ex => ex.imports(ctx, i))
    case a: Adt => extensions.flatMap(ex => ex.imports(ctx, a))
    case _ => List.empty
  }

  def postEmitModules(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = id match {
    case i: Identifier => extensions.flatMap(ex => ex.postEmitModules(ctx, i))
    case e: Enumeration => extensions.flatMap(ex => ex.postEmitModules(ctx, e))
    case d: DTO => extensions.flatMap(ex => ex.postEmitModules(ctx, d))
    case i: Interface => extensions.flatMap(ex => ex.postEmitModules(ctx, i))
    case a: Adt => extensions.flatMap(ex => ex.postEmitModules(ctx, a))
    case _ => List.empty
  }

  def postModuleEmit[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: CSharpTranslatorExtension => (CSTContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
