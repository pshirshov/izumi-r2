package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Alias, Enumeration, Interface}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.csharp.types.CSharpField
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{SimpleStructure, Structure}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.Struct

final case class CSharpClass (
                              id: TypeId,
                              name: String,
                              fields: Seq[CSharpField],
                              implements: List[InterfaceId] = List.empty
                             )(implicit im: CSharpImports, ts: Typespace) {

  def renderHeader(): String = {
    val impls = if (implements.isEmpty) "" else " : " + implements.map(i => i.name).mkString(", ")
    s"public class $name$impls"
  }

  def render(withWrapper: Boolean, withSlices: Boolean, withRTTI: Boolean, withCTORs: Option[String] = None): String = {
      val indent = if (withWrapper) 4 else 0

      val ctorWithParams =
        s"""public $name(${fields.map(f => s"${f.tp.renderType()} ${f.renderMemberName(capitalize = false, uncapitalize = true)}").mkString(", ")}) {
           |${fields.map(f => s"this.${f.renderMemberName()} = ${f.renderMemberName(capitalize = false, uncapitalize = true)};").mkString("\n").shift(4)}
           |}
         """.stripMargin

      val pkg = id.path.toPackage.mkString(".")
      val rtti =
        s"""public static readonly string RTTI_PACKAGE = "$pkg";
           |public static readonly string RTTI_CLASSNAME = "${id.name}";
           |public static readonly string RTTI_FULLCLASSNAME = "${id.wireId}";
           |public string GetPackageName() { return $name.RTTI_PACKAGE; }
           |public string GetClassName() { return $name.RTTI_CLASSNAME; }
           |public string GetFullClassName() { return $name.RTTI_FULLCLASSNAME; }
         """.stripMargin

      val ctors = if (withCTORs.isEmpty) "" else
        s"""private static Dictionary<string, Func<object>> __ctors = new Dictionary<string, Func<object>>();
           |public static void Register(string id, System.Func<object> ctor) {
           |    if ($name.__ctors.ContainsKey(id)) {
           |        $name.__ctors[id] = ctor;
           |    } else {
           |        $name.__ctors.Add(id, ctor);
           |    }
           |}
           |
           |public static void Unregister(string id) {
           |    if ($name.__ctors.ContainsKey(id)) {
           |        $name.__ctors.Remove(id);
           |    }
           |}
           |
           |public static object CreateInstance(string id) {
           |    if (!$name.__ctors.ContainsKey(id)) {
           |        throw new Exception("Unknown class name: " + id + " for interface ${withCTORs.get}.");
           |    }
           |
           |    return $name.__ctors[id]();
           |}
           |
           |static $name() {
           |    var type = typeof(${withCTORs.get});
           |    foreach (var assembly in AppDomain.CurrentDomain.GetAssemblies()) {
           |        foreach (var tp in assembly.GetTypes()) {
           |            if (type.IsAssignableFrom(tp) && !tp.IsInterface) {
           |                var rttiID = tp.GetField("RTTI_FULLCLASSNAME");
           |                if (rttiID != null) {
           |                    $name.Register((string)rttiID.GetValue(null), () => Activator.CreateInstance(tp));
           |                }
           |            }
           |        }
           |    }
           |}
         """.stripMargin

      val content =
        s"""${if (withRTTI) rtti else ""}
           |${fields.map(f => f.renderMember(false)).mkString("\n")}
           |
           |public $name() {
           |${fields.map(f => if(f.tp.getInitValue.isDefined) f.renderMemberName() + " = " + f.tp.getInitValue.get + ";" else "").filterNot(_.isEmpty).mkString("\n").shift(4)}
           |}
           |
           |${if (!fields.isEmpty) ctorWithParams else ""}
           |${if (withSlices) ("\n" + renderSlices()) else ""}
           |${if (withCTORs.isDefined) ctors else ""}
         """.stripMargin

    s"""${if (withWrapper) s"${renderHeader()} {" else ""}
       |${content.shift(indent)}
       |${if (withWrapper) "}" else ""}
       """.stripMargin
  }

  private def renderSlice(i: InterfaceId): String = {
    val interface = ts(i).asInstanceOf[Interface]
    val eid = ts.implId(i)
    val eidStruct = ts.structure.structure(eid)
    val eidClass = CSharpClass(eid, i.name + eid.name, eidStruct, List.empty)

    s"""public ${i.name} To${i.name}() {
       |    var res = new ${i.name}${eid.name}();
       |${eidClass.fields.map(f => s"res.${f.renderMemberName()} = this.${f.renderMemberName()};").mkString("\n").shift(4)}
       |    return res;
       |}
       |
       |public void Load${i.name}(${i.name} value) {
       |${eidClass.fields.map(f => s"this.${f.renderMemberName()} = value.${f.renderMemberName()};").mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  private def renderSlices(): String = {
    implements.map(i => renderSlice(i)).mkString("\n")
  }
}

object CSharpClass {
  def apply(
             id: TypeId,
             name: String,
             fields: Seq[CSharpField],
             implements: List[InterfaceId] = List.empty)
           (implicit im: CSharpImports, ts: Typespace): CSharpClass = new CSharpClass(id, name, fields, implements)

  def apply(id: TypeId,
            name: String,
            st: Struct,
            implements: List[InterfaceId])
           (implicit im: CSharpImports, ts: Typespace): CSharpClass =
    new CSharpClass(id, name, st.all.groupBy(_.field.name)
      .map(f =>
        CSharpField(
          if (f._2.head.defn.variance.nonEmpty) f._2.head.defn.variance.last else f._2.head.field, name,
          if (f._2.length > 1) f._2.map(ef => ef.defn.definedBy.name) else Seq.empty)).toSeq,
      st.superclasses.interfaces ++ implements)

  def apply(id: TypeId,
            st: SimpleStructure)
           (implicit im: CSharpImports, ts: Typespace): CSharpClass =
    new CSharpClass(id, id.name, st.fields.map(f =>
        CSharpField(f, f.name, Seq.empty)),
        List.empty)
}
