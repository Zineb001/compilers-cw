package comp0012.target;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test dynamic variable folding
 */

public class DynamicVariableFoldingTest {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  DynamicVariableFolding dvf = new DynamicVariableFolding();

  @Before
  public void setUpStreams() {
    System.setOut(new PrintStream(outContent));
  }

  @After
  public void cleanUpStreams() {
    System.setOut(null);
  }

  @Test
  public void testMethodOne() {
    assertEquals(1301, dvf.methodOne());
  }

  @Test
  public void testMethodTwoOut() {
    dvf.methodTwo();
    assertEquals("true\n", outContent.toString());
  }

  @Test
  public void testMethodTwoReturn() {
    assertTrue(dvf.methodTwo());
  }

  @Test
  public void testMethodThree() {
    assertEquals(84, dvf.methodThree());
  }

  @Test
  public void testMethodFour() {
    assertEquals(24, dvf.methodFour());
  }


}