package izumi.flow.dsl.simulator.components

import izumi.flow.model.expr.FExpr
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue
import izumi.flow.model.values.FValue.FVField
import jdk.nashorn.api.scripting.ScriptObjectMirror

import scala.jdk.CollectionConverters._
import java.util
import javax.script.SimpleBindings

class NsInterpreter() {
  import javax.script.ScriptEngineManager
  val manager = new ScriptEngineManager
  val engine = manager.getEngineByName("nashorn")

  def interpretNs(iv: FValue, outType: FType, expr: FExpr): FValue = {
    expr match {
      case FExpr.NashornOp(expr, input, output) =>
        assert(outType == output, s"$outType != $output")
        assert(iv.tpe == input, s"${iv.tpe} != $input")

        val b = new util.HashMap[String, AnyRef]()
        val i = buildInput(iv)
        //println(s"INTERPRET $expr on $i")
        b.put("self", i)
        val bindings = new SimpleBindings(b)
        val out = engine.eval(expr, bindings)
        reconstructValue(outType, out)
    }
  }

  private def reconstructValue(outType: FType, out: AnyRef): FValue = {
    outType match {
      case FType.FBool =>
        FValue.FVBool(out.asInstanceOf[Boolean])
      case FType.FInt =>
        val v = (out: Any) match {
          case d: Double =>
            d.intValue()
          case i: Int =>
            i
          case b =>
            scala.sys.error(s"Not an integer: $b, ${b.getClass}")
        }
        FValue.FVInt(v)
      case t: FType.FRecord =>
        val o = out.asInstanceOf[ScriptObjectMirror]
        assert(o.size() == t.fields.size)
        val m = o.entrySet().asScala.map(e => (e.getKey, e.getValue)).toMap
        val fields = t.fields.map {
          f =>
            FVField(f.name, reconstructValue(f.tpe, m(f.name)))
        }
        FValue.FVRecord(fields, t)
      case t: FType.FTuple =>
        val o = out.asInstanceOf[ScriptObjectMirror]
        assert(o.isArray)
        assert(o.size() == t.tuple.size)
        FValue.FVTuple(o.values().asScala.zip(t.tuple).map { case (v, t) => reconstructValue(t, v) }.toList, t)
      case t: FType.FStream =>
        val o = out.asInstanceOf[ScriptObjectMirror]
        assert(o.isArray)
        FValue.FVFiniteStream(o.values().asScala.map(reconstructValue(t, _)).toList, t)
      case u =>
        scala.sys.error(s"Can't process output of type $u, got $out")
    }
  }

  private def buildInput(iv: FValue): AnyRef = {
    iv match {
      case builtin: FValue.FVBuiltin =>
        (builtin match {
          case FValue.FVString(value) =>
            value
          case FValue.FVInt(value) =>
            value
          case FValue.FVBool(value) =>
            value
          case FValue.FVLong(value) =>
            value
          case FValue.FVDouble(value) =>
            value
        }).asInstanceOf[AnyRef]
      case FValue.FVRecord(fields, _) =>
        fields.map(f => (f.name, buildInput(f.value))).toMap.asJava
      case FValue.FVTuple(tuple, _) =>
        tuple.map(v => buildInput(v)).asJava
      case FValue.FVFiniteStream(values, _) =>
        values.map(buildInput).asJava
    }
  }
}
