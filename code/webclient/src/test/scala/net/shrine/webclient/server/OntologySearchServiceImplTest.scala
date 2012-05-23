package net.shrine.webclient.server

import junit.framework.TestCase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.matchers.ShouldMatchers
import org.junit.Test
import net.shrine.webclient.client.widgets.OntologyTree
import net.shrine.ont.data.ShrineSqlOntologyDAO
import net.shrine.ont.index.OntologyTrie
import net.shrine.ont.messaging.Concept
import net.shrine.webclient.client.domain.TermSuggestion
import net.shrine.webclient.client.domain.OntNode

/**
 * @author clint
 * @date May 22, 2012
 */
final class OntologySearchServiceImplTest extends TestCase with AssertionsForJUnit with ShouldMatchers {
  private def shrineSqlStream = getClass.getClassLoader.getResourceAsStream("testShrineWithSyns.sql")
  
  private def dao = new ShrineSqlOntologyDAO(shrineSqlStream)
  
  import OntologySearchServiceImpl.toConcept
  
  private val gender = toConcept("""\\SHRINE\SHRINE\Demographics\Gender\""")
  
  private val male = toConcept("""\\SHRINE\SHRINE\Demographics\Gender\Male\""")
  private val female = toConcept("""\\SHRINE\SHRINE\Demographics\Gender\Female\""")
  private val undifferentiated = toConcept("""\\SHRINE\SHRINE\Demographics\Gender\Undifferentiated\""")
  private val unknown = toConcept("""\\SHRINE\SHRINE\Demographics\Gender\Unknown\""")
  
  val pravastatin = Concept("""\\SHRINE\SHRINE\medications\CV000\CV350\42463""", Some("Pravastatin Sodium"))
  
  @Test
  def testIsLeafTrie {
    val ontTrie = OntologyTrie(dao)
    
    OntologySearchServiceImpl.isLeafTrie(ontTrie) should be(false)
    
    val genderSubTrie = ontTrie.subTrieForPrefix(gender)
    
    OntologySearchServiceImpl.isLeafTrie(genderSubTrie) should be(false)
    
    val maleSubTrie = ontTrie.subTrieForPrefix(male)
    
    OntologySearchServiceImpl.isLeafTrie(maleSubTrie) should be(true)
  }
  
  @Test
  def testIsLeafConcept {
    val ontService = new OntologySearchServiceImpl(shrineSqlStream)
    
    ontService.isLeaf(gender) should be(false)
    
    ontService.isLeaf(male) should be(true)
  }
  
  @Test
  def testToConcept {
    val concept = OntologySearchServiceImpl.toConcept("foo")
    
    concept should not be(null)
    concept.path should equal("foo")
    concept.synonym should be(None)
  }
  
  @Test
  def testToTerm {
    import OntologySearchServiceImpl.toTerm
    
    val maleTerm = toTerm(male)
    
    maleTerm should not be(null)
    maleTerm.getPath should equal(male.path)
    maleTerm.getCategory should equal("Demographics")
    maleTerm.getSimpleName should equal("Male")
    
    val pravastatinTerm = toTerm(pravastatin)
    
    pravastatinTerm should not be(null)
    pravastatinTerm.getPath should equal(pravastatin.path)
    pravastatinTerm.getCategory should equal("medications")
    pravastatinTerm.getSimpleName should equal("Pravastatin Sodium")
    
    toTerm(pravastatin.copy(synonym = None)).getSimpleName should equal("42463")
  }
  
  @Test
  def testIsCodedValue {
    import OntologySearchServiceImpl.{isCodedValue, toConcept}
    
    isCodedValue(male) should be(false)
    isCodedValue(gender) should be(false)
    isCodedValue(toConcept("""\\SHRINE\SHRINE\medications\CV000\CV350\42463""")) should be(true)
    isCodedValue(toConcept("""\\SHRINE\SHRINE\medications\CV000\CV350\""")) should be(true)
    isCodedValue(toConcept("""\\SHRINE\SHRINE\Labs\some\test\""")) should be(true)
  }
  
  @Test
  def testDetermineSimpleNameFor {
    import OntologySearchServiceImpl.{determineSimpleNameFor, toConcept}
    
    determineSimpleNameFor(male) should equal("Male")
    determineSimpleNameFor(gender) should equal("Gender")
    
    determineSimpleNameFor(pravastatin) should equal("Pravastatin Sodium")
    determineSimpleNameFor(pravastatin.copy(synonym = None)) should equal(pravastatin.simpleName)
  }
  
  @Test
  def testGetSuggestions {
    val ontService = new OntologySearchServiceImpl(shrineSqlStream)
    
    val suggestions = ontService.getSuggestions("male", 10)
    
    val maleSuggestion = new TermSuggestion(male.path, "Male", "male", "Male", "Demographics", true)
    
    val femaleSuggestion = new TermSuggestion(female.path, "Female", "male", "Female", "Demographics", true)
    
    import scala.collection.JavaConverters._
    
    val expected = Seq(femaleSuggestion, maleSuggestion)

    def deepEquals(a: TermSuggestion, b: TermSuggestion) {
      a.getCategory should equal(b.getCategory)
      a.getPath should equal(b.getPath)
      a.getHighlight should equal(b.getHighlight)
      a.getSimpleName should equal(b.getSimpleName)
      a.getSynonym should equal(b.getSynonym)
    }
    
    deepEquals(suggestions.asScala.head, expected.head) 
    deepEquals(suggestions.asScala.tail.head, expected.tail.head)
    
    suggestions.asScala should equal(expected)
  }
  
  @Test
  def testGetChildrenFor {
    val ontService = new OntologySearchServiceImpl(shrineSqlStream)
    
    import scala.collection.JavaConverters._
    
    val children = ontService.getChildrenFor(gender.path).asScala.toSet
    
    val expected = Set(male, female, undifferentiated, unknown).map(c => new OntNode(OntologySearchServiceImpl.toTerm(c), true))
    
    children should equal(expected)
  }

  @Test
  def testToOntNode {
    val ontService = new OntologySearchServiceImpl(shrineSqlStream)
   
    val maleOntNodeOption = ontService.toOntNode(male.path)
    
    maleOntNodeOption.isDefined should be(true)
    
    maleOntNodeOption.foreach { maleOntNode =>
      maleOntNode.getChildren.isEmpty should be(true)
      maleOntNode.getSimpleName should equal("Male")
      maleOntNode.getValue should equal(male.path)
      maleOntNode.isLeaf should be(true)
    }
    
    val genderOntNodeOption = ontService.toOntNode(gender.path)
    
    genderOntNodeOption.isDefined should be(true)
    
    genderOntNodeOption.foreach { genderOntNode =>
      genderOntNode.getChildren.isEmpty should be(true)
      genderOntNode.getSimpleName should equal("Gender")
      genderOntNode.getValue should equal(gender.path)
      genderOntNode.isLeaf should be(false)
    }
    
    val demoPath = """\\SHRINE\SHRINE\Demographics\"""
    
    val demoOntNodeOption = ontService.toOntNode(demoPath)
    
    demoOntNodeOption.isDefined should be(true)
    
    demoOntNodeOption.foreach { demoOntNode =>
      demoOntNode.getChildren.isEmpty should be(true)
      demoOntNode.getSimpleName should equal("Demographics")
      demoOntNode.getValue should equal(demoPath)
      demoOntNode.isLeaf should be(false)
    }
    
    ontService.toOntNode("foo") should equal(None)
  }
  
  @Test
  def testGetPathTo {
    val ontService = new OntologySearchServiceImpl(shrineSqlStream)
   
    import scala.collection.JavaConverters._
    
    val pathToMale = ontService.getPathTo(male.path).asScala.toSeq
    
    import OntologySearchServiceImpl.toTerm
    
    def ontNode(path: String, isLeaf: Boolean = false) = new OntNode(toTerm(toConcept(path)), isLeaf)
    
    val expected = Seq(ontNode("""\\SHRINE\SHRINE\"""), ontNode("""\\SHRINE\SHRINE\Demographics\"""), ontNode("""\\SHRINE\SHRINE\Demographics\Gender\"""), ontNode("""\\SHRINE\SHRINE\Demographics\Gender\Male\""", true))
    
    pathToMale should equal(expected)
    
    ontService.getPathTo("foo").isEmpty should be(true)
  }
}