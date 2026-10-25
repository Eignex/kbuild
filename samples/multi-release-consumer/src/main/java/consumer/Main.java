package consumer;

import com.example.BackendInfoKt;

public final class Main {
    public static void main(String[] args) {
        String actual = BackendInfoKt.backend();
        if (!args[0].equals(actual)) {
            throw new AssertionError("Expected " + args[0] + ", got " + actual);
        }
        if (args.length > 1 && !"com.example.multirelease".equals(BackendInfoKt.class.getModule().getName())) {
            throw new AssertionError("Expected the library on the module path");
        }
        System.out.println("Selected " + actual);
    }
}
