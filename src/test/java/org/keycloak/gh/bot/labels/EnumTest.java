package org.keycloak.gh.bot.labels;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnumTest {

    @Test
    public void testToLabel() {
        Assertions.assertEquals("priority/important", Priority.IMPORTANT.toLabel());
        Assertions.assertEquals("label:priority/important", "label:" + Priority.IMPORTANT);
        Assertions.assertEquals("status/missing-information", Status.MISSING_INFORMATION.toLabel());
        Assertions.assertEquals("status/bumped-by-bot", Status.BUMPED_BY_BOT.toLabel());
        Assertions.assertEquals("label:status/missing-information", "label:" + Status.MISSING_INFORMATION);
        Assertions.assertEquals("kind/bug", Kind.BUG.toLabel());
        Assertions.assertEquals("label:kind/bug", "label:" + Kind.BUG);
    }

    @Test
    public void fromLabel() {
        Assertions.assertEquals(Priority.IMPORTANT, Priority.fromLabel("priority/important"));
        Assertions.assertEquals(Status.MISSING_INFORMATION, Status.fromLabel("status/missing-information"));
        Assertions.assertEquals(Status.BUMPED_BY_BOT, Status.fromLabel("status/bumped-by-bot"));
        Assertions.assertEquals(Kind.BUG, Kind.fromLabel("kind/bug"));
    }

    @Test
    public void fromName() {
        Assertions.assertEquals(Priority.IMPORTANT, Priority.fromPriority("important"));
        Assertions.assertEquals(Status.MISSING_INFORMATION, Status.fromStatus("missing-information"));
        Assertions.assertEquals(Kind.BUG, Kind.fromKind("bug"));
    }

    @Test
    public void basicLabels() {
        Assertions.assertEquals("help wanted", Label.HELP_WANTED.toLabel());
        Assertions.assertEquals("label:\"help wanted\"", "label:\"" + Label.HELP_WANTED.toLabel() + "\"");
    }

    @Test
    public void isInstance() {
        Assertions.assertTrue(Priority.isInstance("priority/important"));
        Assertions.assertFalse(Priority.isInstance("priority/something"));
        Assertions.assertFalse(Priority.isInstance("something/important"));
        Assertions.assertFalse(Priority.isInstance("important"));
        Assertions.assertTrue(Status.isInstance("status/missing-information"));
        Assertions.assertFalse(Status.isInstance("something"));
    }

}
