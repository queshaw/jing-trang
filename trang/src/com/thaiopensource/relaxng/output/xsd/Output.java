package com.thaiopensource.relaxng.output.xsd;

import com.thaiopensource.relaxng.output.OutputDirectory;
import com.thaiopensource.relaxng.output.common.ErrorReporter;
import com.thaiopensource.relaxng.output.common.XmlWriter;
import com.thaiopensource.relaxng.edit.Pattern;
import com.thaiopensource.relaxng.edit.GrammarPattern;
import com.thaiopensource.relaxng.edit.AbstractVisitor;
import com.thaiopensource.relaxng.edit.DefineComponent;
import com.thaiopensource.relaxng.edit.ComponentVisitor;
import com.thaiopensource.relaxng.edit.Component;
import com.thaiopensource.relaxng.edit.PatternVisitor;
import com.thaiopensource.relaxng.edit.DataPattern;
import com.thaiopensource.relaxng.edit.Param;
import com.thaiopensource.relaxng.edit.ValuePattern;
import com.thaiopensource.relaxng.edit.ChoicePattern;
import com.thaiopensource.relaxng.edit.ListPattern;
import com.thaiopensource.relaxng.edit.ZeroOrMorePattern;
import com.thaiopensource.relaxng.edit.EmptyPattern;
import com.thaiopensource.relaxng.edit.InterleavePattern;
import com.thaiopensource.relaxng.edit.CompositePattern;
import com.thaiopensource.relaxng.edit.GroupPattern;
import com.thaiopensource.relaxng.edit.OneOrMorePattern;
import com.thaiopensource.relaxng.edit.RefPattern;
import com.thaiopensource.relaxng.edit.OptionalPattern;
import com.thaiopensource.relaxng.edit.IncludeComponent;
import com.thaiopensource.relaxng.edit.ElementPattern;
import com.thaiopensource.relaxng.edit.MixedPattern;
import com.thaiopensource.relaxng.edit.NameNameClass;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**
 * prefix for "xs" needs to be chosen dynamically to avoid conflict
 */
class Output {
  private static final String XSD_URI = "http://www.w3.org/2001/XMLSchema-datatypes";

  private final SchemaInfo si;
  private final ErrorReporter er;
  private final XmlWriter xw;
  private final ComponentVisitor topLevelOutput = new TopLevelOutput();
  private final PatternVisitor simpleTypeOutput = new SimpleTypeOutput();
  private final PatternVisitor groupOutput = new GroupOutput();
  private final PatternVisitor particleOutput = new ParticleOutput();
  private final PatternVisitor typeDefParticleOutput = new TypeDefParticleOutput();
  private final PatternVisitor occursCalculator = new OccursCalculator();

  static void output(SchemaInfo si, OutputDirectory od, ErrorReporter er) throws IOException {
    try {
      new Output(si,
                 er,
                 new XmlWriter(od.getLineSeparator(),
                               od.open(od.MAIN), new String[0],
                               od.getEncoding())).outputSchema(si.getGrammar());
    }
    catch (XmlWriter.WrappedException e) {
      throw e.getIOException();
    }
  }

  private Output(SchemaInfo si, ErrorReporter er, XmlWriter xw) {
    this.si = si;
    this.er = er;
    this.xw = xw;
  }

  private String xs(String name) {
    return "xs:" + name;
  }

  private void outputSchema(GrammarPattern grammar) {
    xw.startElement(xs("schema"));
    xw.attribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
    xw.attribute("elementFormDefault", "qualified");
    xw.attribute("version", "1.0");
    List components = grammar.getComponents();
    for (int i = 0, len = components.size(); i < len; i++)
      ((Component)components.get(i)).accept(topLevelOutput);
    xw.endElement();
    xw.close();
  }

  /**
   * Precondition for calling visit methods in this class is that the child type
   * contains ELEMENT.
   */
  class ParticleOutput extends AbstractVisitor {
    void startWrapper() { }
    void endWrapper() { }

    public Object visitZeroOrMore(ZeroOrMorePattern p) {
      startWrapper();
      xw.startElement(xs("sequence"));
      xw.attribute("minOccurs", "0");
      xw.attribute("maxOccurs", "unbounded");
      p.getChild().accept(particleOutput);
      xw.endElement();
      endWrapper();
      return null;
    }

    public Object visitOneOrMore(OneOrMorePattern p) {
      startWrapper();
      xw.startElement(xs("sequence"));
      xw.attribute("maxOccurs", "unbounded");
      p.getChild().accept(particleOutput);
      xw.endElement();
      endWrapper();
      return null;
    }

    public Object visitOptional(OptionalPattern p) {
      startWrapper();
      xw.startElement(xs("sequence"));
      xw.attribute("minOccurs", "0");
      p.getChild().accept(particleOutput);
      xw.endElement();
      endWrapper();
      return null;
    }

    public Object visitChoice(ChoicePattern p) {
      int nChildren = 0;
      boolean optional = false;
      List children = p.getChildren();
      int len = children.size();
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          nChildren++;
        else if (!ct.equals(ChildType.NOT_ALLOWED))
          optional = true;
      }
      if (optional)
        startWrapper();
      if (nChildren != 1 || optional) {
        xw.startElement(xs("choice"));
        if (optional)
          xw.attribute("minOccurs", "0");
      }
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          child.accept(nChildren == 1 && !optional ? this : particleOutput);
      }
      if (nChildren != 1 || optional)
        xw.endElement();
      if (optional)
        endWrapper();
      return null;
    }

    public Object visitGroup(GroupPattern p) {
      int nChildren = 0;
      List children = p.getChildren();
      int len = children.size();
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          nChildren++;
      }
      if (nChildren != 1)
        xw.startElement(xs("sequence"));
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          child.accept(nChildren == 1 ? this : particleOutput);
      }
      if (nChildren != 1)
        xw.endElement();
      return null;
    }

    public Object visitInterleave(InterleavePattern p) {
      int nChildren = 0;
      List children = p.getChildren();
      int len = children.size();
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          nChildren++;
      }
      // TODO this won't always work
      if (nChildren != 1)
        xw.startElement(xs("all"));
      for (int i = 0; i < len; i++) {
        Pattern child = (Pattern)children.get(i);
        ChildType ct = si.getChildType(child);
        if (ct.contains(ChildType.ELEMENT))
          child.accept(nChildren == 1 ? this : particleOutput);
      }
      if (nChildren != 1)
        xw.endElement();
      return null;
    }

    public Object visitMixed(MixedPattern p) {
      return p.getChild().accept(this);
    }

    public Object visitRef(RefPattern p) {
      startWrapper();
      xw.startElement(xs("group"));
      xw.attribute("ref", p.getName());
      xw.endElement();
      endWrapper();
      return null;
    }

    public Object visitElement(ElementPattern p) {
      startWrapper();
      xw.startElement(xs("element"));
      // TODO deal with name classes
      xw.attribute("name", ((NameNameClass)p.getNameClass()).getLocalName());
      xw.startElement(xs("complexType"));
      Pattern body = p.getChild();
      ChildType ct = si.getChildType(body);
      if (ct.contains(ChildType.ELEMENT)) {
        if (ct.contains(ChildType.TEXT) || ct.contains(ChildType.DATA))
          xw.attribute("mixed", "true");
        body.accept(typeDefParticleOutput);
      }
      else if (ct.contains(ChildType.TEXT))
        xw.attribute("mixed", "true");
      else if (ct.contains(ChildType.DATA)) {
        xw.startElement(xs("simpleContent"));
        xw.startElement(xs("restriction"));
        xw.attribute("base", xs("anyType"));
        xw.startElement(xs("simpleType"));
        body.accept(simpleTypeOutput);
        xw.endElement();
        // attributes go here
        xw.endElement();
        xw.endElement();
      }
      xw.endElement();
      xw.endElement();
      endWrapper();
      return null;
    }
  }


  class GroupOutput extends ParticleOutput {
    void startWrapper() {
      xw.startElement(xs("sequence"));
    }

    void endWrapper() {
      xw.endElement();
    }
  }

  class TypeDefParticleOutput extends ParticleOutput {
    public Object visitElement(ElementPattern p) {
      xw.startElement(xs("sequence"));
      particleOutput.visitElement(p);
      xw.endElement();
      return null;
    }
  }

  // TODO NOTATION
  // TODO multiple pattern facets
  // TODO deal with <ref> including <ref> to sequence
  // TODO determine when representation of list is an approximation and give a warning
  /**
   * Preconditions for calling visit methods in this class are that the child type
   * - contains DATA
   * - does not contains ELEMENT
   * - does not contain TEXT
   */
  class SimpleTypeOutput extends AbstractVisitor {
    public Object visitChoice(ChoicePattern p) {
      List patterns = p.getChildren();
      if (canOutputChoiceAsRestriction(p)) {
        xw.startElement(xs("restriction"));
        xw.attribute("base", xs(((ValuePattern)patterns.get(0)).getType()));
        for (int i = 0, len = patterns.size(); i < len; i++)
          outputEnumeration((ValuePattern)patterns.get(i));
        xw.endElement();
      }
      else
        outputUnion(patterns);
      return null;
    }

    void outputUnion(List patterns) {
      int nDataChildren = 0;
      for (int i = 0, len = patterns.size(); i < len; i++) {
        Pattern pattern = (Pattern)patterns.get(i);
        if (si.getChildType(pattern).contains(ChildType.DATA)
            && nDataChildren++ > 0)
          break;
      }

      if (nDataChildren > 1)
        xw.startElement(xs("union"));
      // TODO use memberTypes attribute if possible

      for (int i = 0, len = patterns.size(); i < len; i++) {
        Pattern pattern = (Pattern)patterns.get(i);
        if (si.getChildType(pattern).contains(ChildType.DATA)) {
          if (nDataChildren > 1)
            xw.startElement(xs("simpleType"));
          pattern.accept(this);
          if (nDataChildren > 1)
            xw.endElement();
        }
      }
      if (nDataChildren > 1)
        xw.endElement();
    }


    private void outputEnumeration(ValuePattern p) {
      // TODO output namespace declarations
      xw.startElement(xs("enumeration"));
      xw.attribute("value", p.getValue());
      xw.endElement();
    }

    public Object visitData(DataPattern p) {
      xw.startElement(xs("restriction"));
      if (p.getDatatypeLibrary().equals(XSD_URI) || p.getDatatypeLibrary().equals("")) {
        xw.attribute("base", xs(p.getType()));
        List params = p.getParams();
        for (int i = 0, len = params.size(); i < len; i++) {
          Param param = (Param)params.get(i);
          xw.startElement(xs(param.getName()));
          xw.attribute("value", param.getValue());
          xw.endElement();
        }
      }
      else {
        // TODO give an error
        xw.attribute("base", xs("string"));
      }
      xw.endElement();
      return null;
    }

    public Object visitValue(ValuePattern p) {
      xw.startElement(xs("restriction"));
      if (p.getDatatypeLibrary().equals(XSD_URI) || p.getDatatypeLibrary().equals("")) {
        xw.attribute("base", xs(p.getType()));
        outputEnumeration(p);
      }
      else {
        // TODO give an error
        xw.attribute("base", xs("string"));
      }
      xw.endElement();
      return null;
    }

    public Object visitList(ListPattern p) {
      Pattern content = p.getChild();
      if (si.getChildType(content).equals(ChildType.EMPTY)) {
        xw.startElement(xs("restriction"));
        xw.attribute("base", xs("token"));
        xw.startElement(xs("length"));
        xw.attribute("value", "0");
        xw.endElement();
        xw.endElement();
      }
      else {
        Occurs occurs = (Occurs)content.accept(occursCalculator);
        boolean occurRestricted = occurs.min != 0 || occurs.max != UNBOUNDED;
        if (occurRestricted) {
          xw.startElement(xs("restriction"));
          xw.startElement(xs("simpleType"));
        }
        xw.startElement(xs("list"));
        xw.startElement(xs("simpleType"));
        content.accept(this);
        xw.endElement();
        xw.endElement();
        if (occurRestricted) {
          xw.endElement();
          if (occurs.min != 0) {
            xw.startElement(xs("minLength"));
            xw.attribute("value", Integer.toString(occurs.min));
            xw.endElement();
          }
          if (occurs.max != UNBOUNDED) {
            xw.startElement(xs("maxLength"));
            xw.attribute("value", Integer.toString(occurs.max));
            xw.endElement();
          }
          xw.endElement();
        }
      }
      return null;
    }

    public Object visitRef(RefPattern p) {
      xw.startElement(xs("restriction"));
      xw.attribute("base", p.getName());
      xw.endElement();
      return null;
    }

    public Object visitOneOrMore(OneOrMorePattern p) {
      return p.getChild().accept(this);
    }

    public Object visitZeroOrMore(ZeroOrMorePattern p) {
      return p.getChild().accept(this);
    }

    public Object visitOptional(OptionalPattern p) {
      return p.getChild().accept(this);
    }

    public Object visitGroup(GroupPattern p) {
      outputUnion(p.getChildren());
      return null;
    }

    public Object visitInterleave(InterleavePattern p) {
      outputUnion(p.getChildren());
      return null;
    }
  }

  static final int UNBOUNDED = Integer.MAX_VALUE;

  static class Occurs {
    Occurs(int min, int max) {
      this.min = min;
      this.max = max;
    }
    static Occurs add(Occurs occ1, Occurs occ2) {
      return new Occurs(occ1.min + occ2.min,
                        occ1.max == UNBOUNDED || occ2.max == UNBOUNDED
                        ? UNBOUNDED
                        : occ1.max + occ2.max);
    }
    final int min;
    final int max;
  }

  class OccursCalculator extends AbstractVisitor {
    public Object visitOptional(OptionalPattern p) {
      return new Occurs(0, ((Occurs)p.getChild().accept(this)).max);
    }

    public Object visitZeroOrMore(ZeroOrMorePattern p) {
      return new Occurs(0, UNBOUNDED);
    }

    public Object visitOneOrMore(OneOrMorePattern p) {
      return new Occurs(((Occurs)p.getChild().accept(this)).min, UNBOUNDED);
    }

    public Object visitData(DataPattern p) {
      return new Occurs(1, 1);
    }

    public Object visitValue(ValuePattern p) {
      return new Occurs(1, 1);
    }

    public Object visitEmpty(EmptyPattern p) {
      return new Occurs(0, 0);
    }

    private Occurs sum(CompositePattern p) {
      Occurs occ = new Occurs(0, 0);
      List children = p.getChildren();
      for (int i = 0, len = children.size(); i < len; i++)
        occ = Occurs.add(occ, (Occurs)((Pattern)children.get(i)).accept(this));
      return occ;
    }

    public Object visitInterleave(InterleavePattern p) {
      return sum(p);
    }

    public Object visitGroup(GroupPattern p) {
      return sum(p);
    }

    public Object visitChoice(ChoicePattern p) {
      List children = p.getChildren();
      Occurs occ = (Occurs)((Pattern)children.get(0)).accept(this);
      for (int i = 1, len = children.size(); i < len; i++) {
        Occurs tem = (Occurs)((Pattern)children.get(i)).accept(this);
        occ = new Occurs(Math.min(occ.min, tem.min),
                         Math.max(occ.max, tem.max));
      }
      return occ;
    }

    public Object visitRef(RefPattern p) {
      return si.getBody(p).accept(this);
    }
  }

  class TopLevelOutput extends AbstractVisitor {
    public Object visitDefine(DefineComponent c) {
      String name = c.getName();
      Pattern body = c.getBody();
      ChildType ct = si.getChildType(body);
      if (name == DefineComponent.START)
        ;
      else {
        if (ct.contains(ChildType.ELEMENT)) {
          xw.startElement(xs("group"));
          xw.attribute("name", c.getName());
          body.accept(groupOutput);
          xw.endElement();
        }
        else if (ct.contains(ChildType.DATA) && !ct.contains(ChildType.TEXT)) {
          xw.startElement(xs("simpleType"));
          xw.attribute("name", c.getName());
          body.accept(simpleTypeOutput);
          xw.endElement();
        }
        if (ct.contains(ChildType.ATTRIBUTE)) {
        }
      }
      return null;
    }
  }

  static boolean canOutputChoiceAsRestriction(ChoicePattern p) {
    List patterns = p.getChildren();
    Pattern first = (Pattern)patterns.get(0);
    if (!(first instanceof ValuePattern))
      return false;
    ValuePattern firstValue = (ValuePattern)first;
    String datatypeLibrary = firstValue.getDatatypeLibrary();
    if (!datatypeLibrary.equals(XSD_URI) && !datatypeLibrary.equals(""))
      return false;
    for (int i = 1, len = patterns.size(); i < len; i++) {
      Pattern pattern = (Pattern)patterns.get(i);
      if (!(pattern instanceof ValuePattern))
        return false;
      ValuePattern value = (ValuePattern)pattern;
      if (!value.getDatatypeLibrary().equals(XSD_URI)
              && !value.getDatatypeLibrary().equals(""))
        return false;
      if (!value.getType().equals(firstValue.getType()))
        return false;
    }
    return true;
  }

}
