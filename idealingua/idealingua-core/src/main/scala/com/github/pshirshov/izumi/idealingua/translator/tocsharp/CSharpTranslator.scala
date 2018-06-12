package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.{CSharpTranslatorExtension, JsonNetExtension, Unity3DExtension}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.csharp.types.CSharpField
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpType}
//import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types._

object CSharpTranslator {
  final val defaultExtensions = Seq(
    JsonNetExtension,
    Unity3DExtension
  )
}

class CSharpTranslator(ts: Typespace, extensions: Seq[CSharpTranslatorExtension]) {
  protected val ctx: CSTContext = new CSTContext(ts, extensions)

  import ctx._

  def translate(): Seq[Module] = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
    ).flatten

    modules
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id), renderService(definition))
  }

  protected def translateDef(definition: TypeDef): Seq[Module] = {
    val defns = definition match {
      case i: Alias =>
        renderAlias(i)
      case i: Enumeration =>
        renderEnumeration(i)
      case i: Identifier =>
        renderIdentifier(i)
      case i: Interface =>
        renderInterface(i)
      case d: DTO =>
        renderDto(d)
      case d: Adt =>
        renderAdt(d)
      case _ =>
        RenderableCogenProduct.empty
    }

    ctx.modules.toSource(definition.id.path.domain, ctx.modules.toModuleId(definition), defns)
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    implicit val ts = this.ts
    implicit val imports = CSharpImports(i, i.id.path.toPackage)
    val structure = typespace.structure.structure(i)

    val struct = CSharpClass(i.id, i.id.name, structure, List.empty)

    val dto =
      s"""${struct.render(withWrapper = true, withSlices = true)}
       """.stripMargin

//    val dto =
//      s"""${struct.render()}
//         |${struct.renderSerialized()}
//         |${struct.renderSlices()}
//         |${renderRegistrations(ts.inheritance.allParents(i.id), i.id.name, imports)}
//       """.stripMargin
//
//    CompositeProduct(dto, imports.renderImports(List("encoding/json", "fmt")), tests)
      CompositeProduct(dto, "using System;\nusing System.Collections;\nusing System.Collections.Generic;")

  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
    implicit val ts: Typespace = this.ts
    implicit val imports = CSharpImports(i, i.id.path.toPackage)
    val cstype = CSharpType(i.target)

    AliasProduct(
      s"""// C# does not natively support full type aliases. They usually
         |// live only within the current file scope, making it impossible
         |// to make them type aliases within another namespace.
         |//
         |// Had it been fully supported, the code would be something like:
         |// using ${i.id.name} = ${cstype.renderType()}
         |//
         |// For the time being, please use the target type everywhere you need.
         """.stripMargin
    )
  }

  protected def renderAdtMember(adtName: String, member: AdtMember)(implicit im: CSharpImports, ts: Typespace): String = {
    val operators =
      s"""    public static explicit operator _${member.name}(${member.name} m) {
         |        return m.value;
         |    }
         |
         |    public static explicit operator ${member.name}(_${member.name} m) {
         |        return new ${member.name}(m);
         |    }
       """.stripMargin

    var operatorsDummy =
      s"""    // We would normally want to have an operator, but unfortunately if it is an interface,
         |    // it will fail on "user-defined conversions to or from an interface are now allowed".
         |    // public static explicit operator _${member.name}(${member.name} m) {
         |    //     return m.value;
         |    // }
         |    //
         |    // public static explicit operator ${member.name}(_${member.name} m) {
         |    //     return new ${member.name}(m);
         |    // }
       """.stripMargin

    val memberType = CSharpType(member.typeId)
    s"""public sealed class ${member.name}: ${adtName} {
       |    public _${member.name} Value { get; private set; }
       |    public ${member.name}(_${member.name} value) {
       |        Value = value;
       |    }
       |
       |${if (member.typeId.isInstanceOf[InterfaceId]) operatorsDummy else operators}
       |}
     """.stripMargin
  }

  protected def renderAdtImpl(adtName: String, members: List[AdtMember])(implicit im: CSharpImports, ts: Typespace): String = {
    s"""${members.map(m => s"using _${m.name} = ${CSharpType(m.typeId).renderType()};").mkString("\n")}
       |
       |public abstract class $adtName {
       |    private $adtName() {}
       |${members.map(m => renderAdtMember(adtName, m)).mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    implicit val ts: Typespace = this.ts
    implicit val imports: CSharpImports = CSharpImports(i, i.id.path.toPackage)

    AdtProduct(renderAdtImpl(i.id.name, i.alternatives), "using System;")
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
      val name = i.id.name
      val decl =
        s"""// $name Enumeration
           |public enum $name {
           |${i.members.map(m => s"$m${if (m == i.members.last) "" else ","}").mkString("\n").shift(4)}
           |}
           |
           |public static class ${name}Helpers {
           |    public static $name From(string value) {
           |        $name v;
           |        if (Enum.TryParse(value, out v)) {
           |            return v;
           |        }
           |        throw new ArgumentOutOfRangeException(value);
           |    }
           |
           |    public static bool IsValid(string value) {
           |        return Enum.IsDefined(typeof($name), value);
           |    }
           |
           |    // The elements in the array are still changeable, please use with care.
           |    private static readonly $name[] all = new $name[] {
           |${i.members.map(m => s"$name.$m${if (m == i.members.last) "" else ","}").mkString("\n").shift(8)}
           |    };
           |
           |    public static $name[] GetAll() {
           |        return ${name}Helpers.all;
           |    }
           |
           |    // Extensions
           |
           |    public static string ToString(this $name e) {
           |        return Enum.GetName(typeof($name), e);
           |    }
           |}
         """.stripMargin

    EnumProduct(
      decl,
      "using System;",
      ""
    )
  }

  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
      implicit val ts = this.ts
      implicit val imports = CSharpImports(i, i.id.path.toPackage)

      val fields = ts.structure.structure(i).all.map(f => CSharpField(f.field, i.id.name))
      val fieldsSorted = fields.sortBy(_.name)
      val csClass = CSharpClass(i.id, i.id.name, fields)
      val prefixLength = i.id.name.length + 1

      val decl =
        s"""${csClass.renderHeader()} {
           |    private static char[] idSplitter = new char[]{':'};
           |${csClass.render(withWrapper = false, withSlices = false).shift(4)}
           |    public override string ToString() {
           |        var suffix = ${fieldsSorted.map(f => f.tp.renderToString(f.renderMemberName(), escape = true)).mkString(" + \":\" + ")};
           |        return "${i.id.name}#" + suffix;
           |    }
           |
           |    public static ${i.id.name} From(string value) {
           |        if (value == null) {
           |            throw new ArgumentNullException("value");
           |        }
           |
           |        if (!value.StartsWith("${i.id.name}#")) {
           |            throw new ArgumentException(string.Format("Expected identifier for type ${i.id.name}, got {0}", value));
           |        }
           |
           |        var parts = value.Substring($prefixLength, value.Length - $prefixLength).Split(idSplitter, StringSplitOptions.None);
           |        if (parts.Length != ${fields.length}) {
           |            throw new ArgumentException(string.Format("Expected identifier for type ${i.id.name} with ${fields.length} parts, got {0} in string {1}", parts.Length, value));
           |        }
           |
           |        var res = new ${i.id.name}();
           |${fieldsSorted.zipWithIndex.map { case (f, index) => s"res.${f.renderMemberName()} = ${f.tp.renderFromString(s"parts[$index]", unescape = true)};"}.mkString("\n").shift(8)}
           |        return res;
           |    }
           |}
         """.stripMargin

    IdentifierProduct(
      decl,
      "using System;"// imports.render(ts)
    )
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    implicit val ts = this.ts
    implicit val imports = CSharpImports(i, i.id.path.toPackage)

    val structure = typespace.structure.structure(i)
    val eid = typespace.tools.implId(i.id)
    val ifaceFields = structure.all.filterNot(f => i.struct.superclasses.interfaces.contains(f.defn.definedBy)).map(f => CSharpField(f.field, eid.name, Seq.empty))

    val struct = CSharpClass(eid, i.id.name + eid.name, structure, List(i.id))
  // .map(f => if (f.defn.variance.nonEmpty) f.defn.variance.last else f.field )
    val ifaceImplements = if (i.struct.superclasses.interfaces.isEmpty) "" else " : " +
      i.struct.superclasses.interfaces.map(ifc => ifc.name).mkString(", ")

    val iface =
      s"""public interface ${i.id.name}$ifaceImplements {
         |${ifaceFields.map(f => f.renderMember(true)).mkString("\n").shift(4)}
         |}
       """.stripMargin

    val companion = struct.render(withWrapper = true, withSlices = true)

//      s"""${struct.render()}
//         |${struct.renderSerialized()}
//         |${struct.renderSlices()}
//         |
//         |// Polymorphic section below. If a new type to be registered, use Register${i.id.name} method
//         |// which will add it to the known list. You can also overwrite the existing registrations
//         |// in order to provide extended functionality on existing models, preserving the original class name.
//         |
//         |type ${i.id.name}Constructor func() ${i.id.name}
//         |
//         |func ctor${eid.name}() ${i.id.name} {
//         |    return &${eid.name}{}
//         |}
//         |
//         |var known${i.id.name}Polymorphic = map[string]${i.id.name}Constructor {
//         |    rtti${eid.name}FullClassName: ctor${eid.name},
//         |}
//         |
//         |// Register${i.id.name} registers a new constructor for a polymorphic type ${i.id.name}
//         |func Register${i.id.name}(className string, ctor ${i.id.name}Constructor) {
//         |    known${i.id.name}Polymorphic[className] = ctor
//         |}
//         |
//         |// Create${i.id.name} creates an instance of type ${i.id.name} in a polymorphic way
//         |func Create${i.id.name}(data map[string]json.RawMessage) (${i.id.name}, error) {
//         |    for className, content := range data {
//         |        ctor, ok := known${i.id.name}Polymorphic[className]
//         |        if !ok {
//         |            return nil, fmt.Errorf("unknown polymorphic type %s for Create${i.id.name}", className)
//         |        }
//         |
//         |        instance := ctor()
//         |        err := json.Unmarshal(content, instance)
//         |        if err != nil {
//         |            return nil, err
//         |        }
//         |
//         |        return instance, nil
//         |    }
//         |
//         |    return nil, fmt.Errorf("empty content for polymorphic type in Create${i.id.name}")
//         |}
//         |${renderRegistrations(ts.inheritance.allParents(i.id), eid.name, imports)}
//       """.stripMargin

    InterfaceProduct(iface, companion, "using System;\nusing System.Collections;\nusing System.Collections.Generic;")
  }

//  protected def renderServiceMethodSignature(i: Service, method: Service.DefMethod, imports: GoLangImports, spread: Boolean = false, withContext: Boolean = false): String = {
//    method match {
//      case m: DefMethod.RPCMethod => {
//        val context = if (withContext) s"context interface{}${if (m.signature.input.fields.isEmpty) "" else ", "}" else ""
//        if (spread) {
//          val fields = m.signature.input.fields.map(f => f.name + " " + GoLangType(f.typeId, imports, ts).renderType()).mkString(", ")
//          s"${m.name.capitalize}($context$fields) ${renderServiceMethodOutputSignature(i, m, imports)}"
//        } else {
//          s"${m.name.capitalize}(${context}input: ${inName(i, m.name)}) ${renderServiceMethodOutputSignature(i, m, imports)}"
//        }
//      }
//    }
//  }
//
//  protected def renderServiceMethodOutputModel(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = method.signature.output match {
//    case _: Struct => s"*${outName(i, method.name)}"
//    case _: Algebraic => s"*${outName(i, method.name)}"
//    case si: Singular => s"${GoLangType(si.typeId, imports, ts).renderType()}"
//  }
//
//  protected def renderServiceMethodOutputSignature(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = {
//    s"(${renderServiceMethodOutputModel(i, method, imports)}, error)"
//  }
//
//  protected def renderServiceClientMethod(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
//    case m: DefMethod.RPCMethod => m.signature.output match {
//      case _: Struct | _: Algebraic =>
//        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
//           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
//           |    outData := &${outName(i, m.name)}{}
//           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
//           |    if err != nil {
//           |        return nil, err
//           |    }
//           |    return outData, nil
//           |}
//       """.stripMargin
//
//      case so: Singular =>
//        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
//           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
//           |    outData := &${GoLangType(so.typeId, imports, ts).renderType(forAlias = true)}
//           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
//           |    if err != nil {
//           |        return nil, err
//           |    }
//           |    return outData, nil
//           |}
//       """.stripMargin
//    }
//  }
//
//  protected def renderServiceClient(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}Client"
//
//    s"""type ${i.id.name} interface {
//       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true)).mkString("\n").shift(4)}
//       |}
//       |
//       |type $name struct {
//       |    ${i.id.name}
//       |    transport irt.ServiceClientTransport
//       |}
//       |
//       |func (v *$name) SetTransport(t irt.ServiceClientTransport) error {
//       |    if t == nil {
//       |        return fmt.Errorf("method SetTransport requires a valid transport, got nil")
//       |    }
//       |
//       |    v.transport = t
//       |    return nil
//       |}
//       |
//       |func (v *$name) SetHTTPTransport(endpoint string, timeout int, skipSSLVerify bool) {
//       |    v.transport = irt.NewHTTPClientTransport(endpoint, timeout, skipSSLVerify)
//       |}
//       |
//       |func New${name}OverHTTP(endpoint string) *$name{
//       |    res := &$name{}
//       |    res.SetHTTPTransport(endpoint, 15000, false)
//       |    return res
//       |}
//       |
//       |${i.methods.map(me => renderServiceClientMethod(i, me, imports)).mkString("\n")}
//     """.stripMargin
//  }
//
//  protected def renderServiceDispatcherHandler(i: Service, method: Service.DefMethod): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""case "${m.name}": {
//         |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"dataIn, ok := data.(*${inName(i, m.name)})\n    if !ok {\n        return nil, fmt.Errorf(" + "\"invalid input data object for method " + m.name + "\")\n    }"}
//         |    return v.service.${m.name.capitalize}(context${if(m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"dataIn.${f.name.capitalize}()").mkString(", ")})
//         |}
//         |
//       """.stripMargin
//  }
//
//  protected def renderServiceDispatcherPreHandler(i: Service, method: Service.DefMethod): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""case "${m.name}": ${if (m.signature.input.fields.isEmpty) "return nil, nil" else s"return &${inName(i, m.name)}{}, nil"}""".stripMargin
//  }
//
//  protected def renderServiceDispatcher(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}Dispatcher"
//
//    s"""type ${i.id.name}Server interface {
//       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true, withContext = true)).mkString("\n").shift(4)}
//       |}
//       |
//       |type $name struct {
//       |    service ${i.id.name}Server
//       |}
//       |
//       |func (v *$name) SetServer(s ${i.id.name}Server) error {
//       |    if s == nil {
//       |        return fmt.Errorf("method SetServer requires a valid server implementation, got nil")
//       |    }
//       |
//       |    v.service = s
//       |    return nil
//       |}
//       |
//       |func (v *$name) GetSupportedService() string {
//       |    return "${i.id.name}"
//       |}
//       |
//       |func (v *$name) GetSupportedMethods() []string {
//       |    return []string{
//       |${i.methods.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"," else "").mkString("\n").shift(8)}
//       |    }
//       |}
//       |
//       |func (v *$name) PreDispatchModel(context interface{}, method string) (interface{}, error) {
//       |    switch method {
//       |${i.methods.map(m => renderServiceDispatcherPreHandler(i, m)).mkString("\n").shift(8)}
//       |        default:
//       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
//       |    }
//       |}
//       |
//       |func (v *$name) Dispatch(context interface{}, method string, data interface{}) (interface{}, error) {
//       |    switch method {
//       |${i.methods.map(m => renderServiceDispatcherHandler(i, m)).mkString("\n").shift(8)}
//       |        default:
//       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
//       |    }
//       |}
//       |
//       |func New${name}(service ${i.id.name}Server) *$name{
//       |    res := &$name{}
//       |    res.SetServer(service)
//       |    return res
//       |}
//     """.stripMargin
//  }
//
//  protected def renderServiceServerDummy(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}ServerDummy"
//    s"""// $name is a dummy for implementation references
//       |type $name struct {
//       |    // Implements ${i.id.name}Server interface
//       |}
//       |
//       |${i.methods.map(m => s"func (d *$name) " + renderServiceMethodSignature(i, m, imports, spread = true, withContext = true) + s""" {\n    return nil, fmt.Errorf("Method not implemented.")\n}\n""").mkString("\n")}
//     """.stripMargin
//  }
//
//  protected def renderServiceMethodOutModel(i: Service, name: String, out: Service.DefMethod.Output, imports: GoLangImports): String = out match {
//    case st: Struct => renderServiceMethodInModel(i, name, st.struct, imports)
//    case al: Algebraic => renderAdtImpl(name, al.alternatives, imports, withTest = false)
//    case _ => s""
//  }
//
//  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure, imports: GoLangImports): String = {
//    val struct = GoLangStruct(name, DTOId(i.id, name), List.empty,
//      structure.fields.map(ef => GoLangField(ef.name, GoLangType(ef.typeId, imports, ts), name, imports, ts)),
//      imports, ts
//    )
//    s"""${struct.render(makePrivate = true, withTest = false)}
//       |${struct.renderSerialized(makePrivate = true)}
//     """.stripMargin
//  }
//
//  protected def renderServiceMethodModels(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""${if(m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, inName(i, m.name), m.signature.input, imports)}
//         |${renderServiceMethodOutModel(i, outName(i, m.name), m.signature.output, imports)}
//       """.stripMargin
//
//  }
//
//  protected def renderServiceModels(i: Service, imports: GoLangImports): String = {
//    i.methods.map(me => renderServiceMethodModels(i, me, imports)).mkString("\n")
//  }

  protected def renderService(i: Service): RenderableCogenProduct = {
//    val imports = GoLangImports(i, i.id.domain.toPackage, List.empty)
//
//    val svc =
//      s"""// ============== Service models ==============
//         |${renderServiceModels(i, imports)}
//         |
//           |// ============== Service Client ==============
//         |${renderServiceClient(i, imports)}
//         |
//           |// ============== Service Dispatcher ==============
//         |${renderServiceDispatcher(i, imports)}
//         |
//           |// ============== Service Server Dummy ==============
//         |${renderServiceServerDummy(i, imports)}
//         """.stripMargin
//
//    ServiceProduct(svc, imports.renderImports(Seq("encoding/json", "fmt", "irt")))
    CompositeProduct("/*service*/")
  }
}

