package comp0012.target;

public class ConstantVariableFolding {

  public int methodOne() {
    int a = 62;
    int b = (a + 764) * 3;
    return b + 1234 - a;
  }

  public double methodTwo() {
    double i = 0.67;
    int j = 1;
    return i + j;
  }

  public boolean methodThree() {
    int x = 12345;
    int y = 54321;
    return x > y;
  }

  public long methodFour() {
    long x = 4835783423L;
    long y = 1;
    long z = x + y;
    boolean comp = x > y;
    return z;
  }

  public int methodFive() {
    int x = 1000;
    int y = 1;
    int z = x + y;
    boolean comp = x > y;
    return z;
  }

  public int methodSeven() {
    int a = 1000001;
    int b = 1000002;
    int c = 1000003;

    return a + b + c;
  }

  public int methodEight() {
    return 1 + 2 + 3 + 4 + 5;
  }

  public int methodTen() {
    int a = 1;
    int b = 2;
    int c = 3;
    int d = b + c;
    return a;
  }
}