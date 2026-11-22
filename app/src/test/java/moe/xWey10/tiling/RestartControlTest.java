package moe.xWey10.tiling;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RestartControlTest {
    private SharedPreferences registration;
    private RestartControl control;

    @Before public void create() {
        registration = RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext()
                .getSharedPreferences("restart-test", Context.MODE_PRIVATE);
        registration.edit().clear().commit();
        control = new RestartControl(registration);
    }

    @Test public void missingModuleIsUnavailableAndNoRequestCanBeClaimed() {
        assertEquals("unavailable", control.request(100).getString("state"));
        assertNull(control.claim("unknown", 4321, 101));
        control.register("first-start", 1, 4321, 102);
        assertNull(control.claim("first-start", 4321, 103));
    }

    @Test public void requestIsBoundToProcessSessionAndConsumedBeforeRestart() {
        control.register("session-A", 1, 4321, 100);
        Bundle request = control.request(110);
        assertNull(control.claim("session-B", 4321, 111));
        assertNull(control.claim("session-A", 4322, 111));
        assertEquals("accepted", control.claim("session-A", 4321, 112).getString("state"));
        assertNull(control.claim("session-A", 4321, 113));
        assertEquals("accepted", control.status(request.getString("id"), 114).getString("state"));
    }

    @Test public void duplicateClicksCoalesceUntilNewProcessRegisters() {
        control.register("session-A", 1, 4321, 100);
        String id = control.request(110).getString("id");
        assertEquals(id, control.request(120).getString("id"));
        control.claim("session-A", 4321, 130);
        assertEquals(id, control.request(140).getString("id"));
        control.register("session-B", 1, 4322, 200);
        assertEquals("restarted", control.status(id, 201).getString("state"));
        assertNull(control.claim("session-B", 4322, 202));
        assertNotEquals(id, control.request(210).getString("id"));
    }

    @Test public void expiredRequestCannotTriggerDelayedRestart() {
        control.register("session-A", 1, 4321, 100);
        Bundle request = control.request(110);
        long expired = 110 + RestartControl.REQUEST_TTL_MS + 1;
        assertNull(control.claim("session-A", 4321, expired));
        assertEquals("expired", control.status(request.getString("id"), expired).getString("state"));
        control.register("session-B", 1, 4322, expired + 1);
        assertNull(control.claim("session-B", 4322, expired + 2));
    }

    @Test public void processGenerationConfirmsRestartEvenIfPidIsReused() {
        control.register("session-A", 1, 4321, 100);
        String id = control.request(110).getString("id");
        control.claim("session-A", 4321, 120);
        control.register("session-B", 1, 4321, 150);
        Bundle status = control.status(id, 160);
        assertEquals("restarted", status.getString("state"));
        assertEquals("session-B", status.getString("session"));
        assertNull(control.claim("session-A", 4321, 170));
    }

    @Test public void providerRecreationRetainsRegistrationButCannotReplayRequest() {
        control.register("session-A", 1, 4321, 100);
        control.request(110);
        RestartControl recreated = new RestartControl(registration);
        assertNull(recreated.claim("session-A", 4321, 120));
        Bundle newRequest = recreated.request(130);
        assertEquals("pending", newRequest.getString("state"));
        assertEquals("session-A", newRequest.getString("session"));
        assertNotNull(recreated.claim("session-A", 4321, 140));
    }
}
