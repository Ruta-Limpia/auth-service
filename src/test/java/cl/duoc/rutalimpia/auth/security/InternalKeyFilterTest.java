package cl.duoc.rutalimpia.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternalKeyFilterTest {

    @Test
    void noArrancaSinClaveInterna() {
        assertThatThrownBy(() -> new InternalKeyFilter("  ", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalKeyFilter(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
