package izumi.distage.model.plan.repr

import izumi.distage.model.reflection.DIKey.SetKeyMeta

import scala.annotation.nowarn
import izumi.distage.model.reflection._
import izumi.fundamentals.collections.IzCollections._
import izumi.reflect.macrortti.LightTypeTagRef.SymName
import izumi.reflect.macrortti.{LTTRenderables, LightTypeTagRef, RuntimeAPI}

trait DIConsoleColors extends IzConsoleColors

class KeyMinimizer(
  allKeys: Set[DIKey],
  colors: Boolean,
) extends DIConsoleColors {

  override protected def colorsEnabled(): Boolean = colors

  def renderKey(key: DIKey): String = {
    renderKey(key, renderType)
  }

  def renderType(tpe: SafeType): String = {
    import minimizedLTTRenderables.RenderableSyntax
    val base = tpe.tag.ref.render()(minimizedLTTRenderables.r_LightTypeTag)
    styled(base, c.MAGENTA)
  }

  @nowarn("msg=Unused import")
  private[this] val index: Map[String, Int] = {
    import scala.collection.compat._
    allKeys.iterator
      .flatMap(extract)
      .map(name => name.split('.').last -> name)
      .toMultimapView
      .mapValues(_.size)
      .toMap
  }

  private[this] val minimizedLTTRenderables = new LTTRenderables {
    override def r_SymName(sym: SymName, hasPrefix: Boolean): String = {
      val shortname = sym.name.split('.').last
      if (hasPrefix) {
        shortname
      } else {
        val withSameName = index.getOrElse(shortname, 0)
        if (withSameName <= 1) shortname else sym.name
      }
    }
  }

  private[this] def showKeyData(prefix: String, value: String, idx: Option[Int] = None) = {
    val prefixRepr = styled(s"{$prefix.", c.GREEN)
    val suffixRepr = styled(s"}", c.GREEN)
    val idxrepr = idx.map(i => styled(i.toString, c.RED)).getOrElse("")

    s"$prefixRepr$value$suffixRepr$idxrepr"
  }

  @inline private[this] def renderKey(key: DIKey, rendertype: SafeType => String): String = {
    // in order to make idea links working we need to put a dot before Position occurence and avoid using #
    key match {
      case DIKey.TypeKey(tpe, idx) =>
        showKeyData("type", rendertype(tpe), idx)

      case DIKey.IdKey(tpe, id, idx) =>
        val asString = id.toString
        val fullId = if (asString.contains(":") || asString.contains("#")) {
          s"[$asString]"
        } else {
          asString
        }

        showKeyData("id", s"${rendertype(tpe)}${styled("@" + fullId, c.UNDERLINED, c.BLUE)}", idx)

      case DIKey.ProxyInitKey(proxied) =>
        showKeyData("proxyinit", renderKey(proxied))

      case DIKey.ProxyControllerKey(proxied, _) =>
        showKeyData("proxyref", renderKey(proxied))

      case DIKey.EffectKey(key, _) =>
        showKeyData("effect", renderKey(key))

      case DIKey.ResourceKey(key, _) =>
        showKeyData("resource", renderKey(key))

      case DIKey.SetElementKey(set, reference, disambiguator) =>
        val base = s"${renderKey(set)}/${renderKey(reference)}"
        val drepr = (disambiguator match {
          case SetKeyMeta.NoMeta =>
            None
          case SetKeyMeta.WithImpl(disambiguator) =>
            Some(s"impl:${disambiguator.hashCode}")
          case SetKeyMeta.WithAutoset(base) =>
            Some(s"autoset:${renderKey(base)}")
        }).map(v => "#" + v).getOrElse("")

        val fullDis = styled(drepr, c.BLUE)
        showKeyData("set", s"$base$fullDis")
    }
  }

  private[this] def extract(key: DIKey): Set[String] = {
    key match {
      case k: DIKey.TypeKey =>
        extract(k.tpe)

      case k: DIKey.IdKey[_] =>
        extract(k.tpe)

      case p: DIKey.ProxyControllerKey =>
        extract(p.tpe) ++ extract(p.proxied)

      case p: DIKey.ProxyInitKey =>
        extract(p.tpe) ++ extract(p.proxied)

      case s: DIKey.SetElementKey =>
        extract(s.tpe) ++ extract(s.reference)

      case r: DIKey.ResourceKey =>
        extract(r.tpe) ++ extract(r.tpe)

      case e: DIKey.EffectKey =>
        extract(e.tpe) ++ extract(e.tpe)
    }
  }

  private[this] def extract(key: SafeType): Set[String] = {
    RuntimeAPI
      .unpack(key.tag.ref match {
        case reference: LightTypeTagRef.AbstractReference =>
          reference
      }).map(_.ref.name)
  }

}

object KeyMinimizer {
  def apply(allKeys: Set[DIKey], colors: Boolean): KeyMinimizer = new KeyMinimizer(allKeys, colors)
}
