package ascore.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AlertBufferTest {

	@Test
	void keepsTheNewestFifty() {
		AlertBuffer buffer = new AlertBuffer();
		for (int i = 0; i < 60; i++) buffer.add("alert-" + i);

		assertEquals(AlertBuffer.CAPACITY, buffer.recent().size());
		assertEquals("alert-59", buffer.recent().getFirst().get("alert"));
		assertEquals("alert-10", buffer.recent().getLast().get("alert"));
	}

}
