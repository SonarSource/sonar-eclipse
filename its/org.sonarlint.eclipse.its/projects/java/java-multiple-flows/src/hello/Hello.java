package hello;

import java.util.logging.Logger;
import java.util.logging.Level;

public class Hello {

  private static final Logger LOG = Logger.getGlobal();

  private Hello() {
    // Nothing
  }

  public static void main(String[] args) {
    doSomethingWith(42);
  }

  private static void doSomethingWith(final int value) {
    String arg = "polop";
    if (value == 42) {
      LOG.warning("Expect a NPE due to 42!");
      arg = null;
    } else if (value == 24) {
      LOG.warning("Expect a NPE due to 24!");
      arg = null;
    }
    doAnotherThingWith(arg);
  }

  private static void doAnotherThingWith(String param) {
    if (LOG.isLoggable(Level.INFO)) {
      LOG.info(String.format("Param has length %d", param.length()));
    }
  }
}
