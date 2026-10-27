import org.example.ReleaseLibrary;
import org.example.MultiplatformLibrary;

class Main {
    public static void main(String[] args) {
        if (!new ReleaseLibrary().message().equals("hello from release") ||
            !new MultiplatformLibrary().message().equals("hello from multiplatform")) {
            throw new AssertionError("Published downstream libraries failed");
        }
        System.out.println("Anonymous downstream resolution passed");
    }
}
