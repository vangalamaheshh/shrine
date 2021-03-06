package net.shrine.config

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.matchers.ShouldMatchers
import org.spin.tools.JAXBUtils

import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlRootElement
import junit.framework.TestCase

/**
 * @author Andrew McMurry, MS
 * @author clint 
 * @date Jan 6, 2010
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
object AdapterMappingsTest {
  private val CORE_KEY_DEMOGRAPHICS_0_9 = """\\i2b2\i2b2\Demographics\Age\0-9 years old\"""
  private val CORE_KEY_TEST = """\\i2b2\i2b2\TEST\KEY\"""
  private val CORE_KEY_INVALID = """THIS IS NOT A VALID GLOBAL KEY"""
  private val LOCAL_KEY_DEMOGRAPHICS_AGE_4 = """\\i2b2\LOCAL\DEM|AGE:4"""
  private val LOCAL_KEY_DEMOGRAPHICS_AGE_TEST = """\\i2b2\LOCAL\DEM|AGE:TEST"""
}

final class AdapterMappingsTest extends TestCase with AssertionsForJUnit with ShouldMatchers {
  import AdapterMappingsTest._

  private val mappings = (new ClasspathAdapterMappingsSource("AdapterMappings_DEM_AGE_0_9.xml")).load

  @Test
  def testGetMappings {
    mappings.localTermsFor(CORE_KEY_INVALID) should not be (null)
    mappings.localTermsFor(CORE_KEY_INVALID).size should equal(0)

    mappings.localTermsFor(CORE_KEY_DEMOGRAPHICS_0_9) should not be (null)
    mappings.localTermsFor(CORE_KEY_DEMOGRAPHICS_0_9).size should equal(10)
  }

  @Test
  def testAddMapping {
    (mappings + (CORE_KEY_DEMOGRAPHICS_0_9 -> LOCAL_KEY_DEMOGRAPHICS_AGE_4)).localTermsFor(CORE_KEY_DEMOGRAPHICS_0_9).size should equal(10)
    
    ((mappings + (CORE_KEY_DEMOGRAPHICS_0_9 -> LOCAL_KEY_DEMOGRAPHICS_AGE_4)) eq mappings) should be(true)

    (mappings + (CORE_KEY_DEMOGRAPHICS_0_9 -> LOCAL_KEY_DEMOGRAPHICS_AGE_TEST)).localTermsFor(CORE_KEY_DEMOGRAPHICS_0_9).size should equal(11)

    mappings.localTermsFor(CORE_KEY_TEST).size should equal(0)
    
    (mappings + (CORE_KEY_TEST -> LOCAL_KEY_DEMOGRAPHICS_AGE_TEST)).localTermsFor(CORE_KEY_TEST).size should equal(1)
  }

  @Test
  def testSerialize {
    val m = AdapterMappings.empty  ++ ("core1" -> "local1", 
							    	   "core1" -> "local2",
							    	   "core2" -> "local1",
							    	   "core2" -> "local2",
							    	   "core2" -> "local3")

    val jaxbable = m.jaxbable
    
    val xml = JAXBUtils.marshalToString(jaxbable)
    
    val jaxbable1 = JAXBUtils.unmarshal(xml, classOf[JaxbableAdapterMappings])
    
    val xml1 = JAXBUtils.marshalToString(jaxbable1)

    xml should equal(xml1)
    
    AdapterMappings(jaxbable1) should equal(m)
  }
  
  @Test
  def testEmpty {
    AdapterMappings.empty.size should equal(0)
  }
}