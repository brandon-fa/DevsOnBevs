
import com.littleisles.domain.ord.*;
public class LocateCheck {
  public static void main(String[] args) {
    CityBounds b = new CityBounds(45.45, 45.56, -73.68, -73.40);
    for (String a : new String[]{
      "6700 Grande Allée",
      "6700 Grande Allee",
      "6700 Avenue Auteuil",
      "6700 avenue auteuil",
      "4918 Allee des Sorbiers"
    }) {
      try {
        double[] p = AddressLocator.locate(a, b);
        System.out.println(a + " -> " + p[0] + ", " + p[1]);
      } catch (Exception e) {
        System.out.println(a + " -> ERR " + e.getMessage());
      }
    }
  }
}
