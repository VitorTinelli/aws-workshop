package satc.workshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"workshop.consumidor.ativo=false", "workshop.infra.ativo=false"})
class WorkshopApplicationTests {

	@Test
	void contextLoads() {
	}

}
