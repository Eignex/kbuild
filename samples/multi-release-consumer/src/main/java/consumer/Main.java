package consumer;

import com.example.BackendInfoKt;

public final class Main {
    public static void main(String[] args) {
        String actual = BackendInfoKt.backend();
        if (!args[0].equals(actual)) {
            throw new AssertionError("Expected " + args[0] + ", got " + actual);
        }
        System.out.println("Selected " + actual);
    }
}
