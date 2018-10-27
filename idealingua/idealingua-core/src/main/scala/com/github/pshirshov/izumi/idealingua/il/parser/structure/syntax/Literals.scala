package com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax

import fastparse._
import fastparse.NoWhitespace._

// based on fastparse/scalaparse

object Literals {
  /**
    * Parses all whitespace, excluding newlines. This is only
    * really useful in e.g. {} blocks, where we want to avoid
    * capturing newlines so semicolon-inference would work
    */
  def WS[_:P] = P(NoCut(NoTrace((Basic.WSChars | Literals.Comment).rep)))

  /**
    * Parses whitespace, including newlines.
    * This is the default for most things
    */
  def WL0[_:P] = P(NoTrace((Basic.WSChars | Literals.Comment | Basic.Newline).rep))(sourcecode.Name("WL"))
  def WL[_:P] = P(NoCut(WL0))

  def Semi[_:P] = P(WS ~ Basic.Semi)
  def Semis[_:P] = P(Semi.rep(1) ~ WS)
  def Newline[_:P] = P(WL ~ Basic.Newline)

  def NotNewline[_:P]: P0 = P(&(WS ~ !Basic.Newline))
  def OneNLMax[_:P]: P0 = {
    def ConsumeComments[_:P] = P((Basic.WSChars.? ~ Literals.Comment ~ Basic.WSChars.? ~ Basic.Newline).rep)
    P(NoCut(WS ~ Basic.Newline.? ~ ConsumeComments ~ NotNewline))
  }
  def TrailingComma[_:P]: P0 = P(("," ~ WS ~ Basic.Newline).?)

//  def Pattern: P0

  object Literals {

    import Basic._

    def Float[_:P] = {
      def Thing = P(DecNum ~ Exp.? ~ FloatType.?)

      def Thing2 = P("." ~ Thing | Exp ~ FloatType.? | Exp.? ~ FloatType)

      P("." ~ Thing | DecNum ~ Thing2)
    }

    def Int[_:P] = P((HexNum | DecNum) ~ CharIn("Ll").?)

    def Bool[_:P] = P(Key.W("true") | Key.W("false"))

    // Comments cannot have cuts in them, because they appear before every
    // terminal node. That means that a comment before any terminal will
    // prevent any backtracking from working, which is not what we want!
    def CommentChunk[_:P] = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
    def MultilineComment[_:P]: P0 = P("/*" ~/ CommentChunk.rep ~ "*/")
    def SameLineCharChunks[_:P] = P(CharsWhile(c => c != '\n' && c != '\r') | !Basic.Newline ~ AnyChar)
    def LineComment[_:P] = P("//" ~ SameLineCharChunks.rep ~ &(Basic.Newline | End))
    def Comment[_:P]: P0 = P(MultilineComment | LineComment)

    def Null[_:P] = Key.W("null")

    def OctalEscape[_:P] = P(Digit ~ Digit.? ~ Digit.?)
    def Escape[_:P] = P("\\" ~/ (CharIn("""btnfr'\"]""") | OctalEscape | UnicodeEscape))

    def Char[_:P] = {
      // scalac 2.10 crashes if PrintableChar below is substituted by its body
      def PrintableChar = CharPred(CharPredicates.isPrintableChar)

      P((Escape | PrintableChar) ~ "'")
    }


    def TQ[_:P] = P( "\"\"\"" )
    /**
      * Helper to quickly gobble up large chunks of un-interesting
      * characters. We break out conservatively, even if we don't know
      * it's a "real" escape sequence: worst come to worst it turns out
      * to be a dud and we go back into a CharsChunk next rep
      */
    def StringChars[_:P] = P( CharsWhile(c => c != '\n' && c != '"' && c != '\\' && c != '$') )
    def NonTripleQuoteChar[_:P] = P( "\"" ~ "\"".? ~ !"\"" | CharIn("\\$\n") )
    def TripleChars[_:P] = P( (StringChars | NonTripleQuoteChar).rep )
    def TripleTail[_:P] = P( TQ ~ "\"".rep )
    def SingleChars[_:P](allowSlash: Boolean) = {
      def LiteralSlash = P( if(allowSlash) "\\" else Fail )
      def NonStringEnd = P( !CharIn("\n\"") ~ AnyChar )
      P( (StringChars | LiteralSlash | Escape | NonStringEnd ).rep )
    }
    def Str[_:P] = {
      P {
          (TQ ~/ TripleChars.! ~ TripleTail) |
          ("\"" ~/ SingleChars(false).! ~ "\"")
      }
    }

  }


}
