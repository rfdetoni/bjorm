package dev.bjorm.examples;
import dev.bjorm.*;
import javax.sql.DataSource;
import java.util.UUID;
public final class Example {
    private Example() {}
    public static void run(DataSource ds) {
        var db = Bjorm.open(ds, User_BjormMapper.INSTANCE);
        var user = new User(UUID.randomUUID(), "Ana", 28);
        user.insert(db); // Optional Active Record; no global singleton
        var loaded = db.find(User.class, user.id());
        var adults = db.list(User.class, User_.age.gt(18).and(User_.name.like("A%")));
        db.tx(tx -> {
            tx.update(new User(loaded.id(), "Ana Maria", 29));
            tx.insert(new User(UUID.randomUUID(), "Bruno", 30));
        });
    }
}
