package consumer;

import com.example.BackendInfoKt;

/** Android entry point retained by R8. */
public final class AndroidConsumer {
    public static String backend() { return BackendInfoKt.backend(); }
}
