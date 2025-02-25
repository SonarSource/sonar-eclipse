package org.sonarlint.eclipse.ui.internal.util;

public class DogfoodingUtils {
  private DogfoodingUtils() {
    // static stuff
  }
  
  public static final String SONARSOURCE_DOGFOODING_ENV_VAR_KEY = "SONARSOURCE_DOGFOODING";
  
  public static boolean isDogfoodingEnvironment() {
    try {
      String value = System.getenv(SONARSOURCE_DOGFOODING_ENV_VAR_KEY);
      return value != null && value.equals("1");
    } catch (SecurityException e) {
      return false;
    }
  }

}
