package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.util.UUID;
public final class Example {
    private Example() {}
    public static void run(DataSource ds) {
        var db = Bjorm.open(ds, User_BjormMapper.INSTANCE);
        var user = new User(UUID.randomUUID(), "Ana", 28);
        user.insert(db); // Optional Active Record; no global singleton
        var loaded = db.find(User.class, user.id());
        // Preferred query API: generated typed WUser path, explicit DB context.
        var u = WUser.user;
        var adults = WUser.find(db)
            .where(u.age.gt(18).and(u.name.startsWith("A")))
            .orderBy(u.name.asc())
            .limit(50)
            .fetch();
        db.tx(tx -> {
            tx.update(new User(loaded.id(), "Ana Maria", 29));
            tx.insert(new User(UUID.randomUUID(), "Bruno", 30));
        });
    }
}
