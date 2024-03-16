package mod.wurmunlimited.npcs.db;

import com.wurmonline.server.Constants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.shared.exceptions.WurmServerException;
import mod.wurmunlimited.npcs.CrafterMod;

import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.logging.Logger;

public class CrafterDatabase {
    private static final Logger logger = Logger.getLogger(CrafterDatabase.class.getName());
    private static String dbString = "";
    private static boolean created = false;
    public static Clock clock = Clock.systemUTC();
    private static final Map<Creature, String> tags = new HashMap<>();
    private static final Map<Creature, Currency> currencies = new HashMap<>();

    public interface Execute {
        void run(Connection db) throws SQLException;
    }

    public static class FailedToSaveSkills extends WurmServerException {
        private FailedToSaveSkills() {
            super("An error occurred when attempting to save Crafter skills.");
        }
    }

    private static void init() throws SQLException {
        try (Connection conn = DriverManager.getConnection(dbString)) {
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS saved_skills (" +
                    "contract_id INTEGER," +
                    "skill_id INTEGER," +
                    "skill_level REAL," +
                    "UNIQUE (contract_id, skill_id) ON CONFLICT REPLACE" +
                    ");").execute();

            conn.prepareStatement("CREATE TABLE IF NOT EXISTS donated_tools (" +
                    "crafter_id INTEGER," +
                    "item_id INTEGER," +
                    "UNIQUE (crafter_id, item_id) ON CONFLICT REPLACE" +
                    ");").execute();
        }

        created = true;
    }

    private static void execute(Execute execute) throws SQLException {
        Connection db = null;
        try {
            if (dbString.isEmpty())
                dbString = "jdbc:sqlite:" + Constants.dbHost + "/sqlite/" + CrafterMod.dbName;
            if (!created) {
                init();
            }
            db = DriverManager.getConnection(dbString);
            execute.run(db);
        } finally {
            try {
                if (db != null)
                    db.close();
            } catch (SQLException e1) {
                logger.warning("Could not close connection to database.");
                e1.printStackTrace();
            }
        }
    }

    @SuppressWarnings("SqlResolve")
    public static Map<Integer, Double> loadSkillsFor(Item contract) throws SQLException {
        Map<Integer, Double> skills = new HashMap<>();

        execute(db -> {
            PreparedStatement ps = db.prepareStatement("SELECT skill_id, skill_level FROM saved_skills WHERE contract_id=?;");
            ps.setLong(1, contract.getWurmId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                skills.put(rs.getInt(1), rs.getDouble(2));
            }
        });

        return skills;
    }

    @SuppressWarnings("SqlResolve")
    public static void saveSkillsFor(Creature crafter, Item writ) throws FailedToSaveSkills {
        try {
            execute(db -> {
                db.setAutoCommit(false);
                //noinspection SqlResolve
                for (Map.Entry<Integer, Skill> entry : crafter.getSkills().getSkillTree().entrySet()) {
                    PreparedStatement ps = db.prepareStatement("INSERT INTO saved_skills VALUES (?, ?, ?);");
                    ps.setLong(1, writ.getWurmId());
                    ps.setInt(2, entry.getKey());
                    ps.setDouble(3, entry.getValue().getKnowledge());
                    ps.execute();
                }
                db.commit();
            });
        } catch (SQLException e) {
            logger.warning("Failed to update tag for " + crafter.getName() + ".");
            e.printStackTrace();
            throw new FailedToSaveSkills();
        }
    }

    @SuppressWarnings("SqlResolve")
    public static Set<Long> getGivenToolsFor(Creature crafter) throws SQLException {
        Set<Long> tools = new HashSet<>();

        execute(db -> {
            PreparedStatement ps = db.prepareStatement("SELECT item_id FROM donated_tools WHERE crafter_id=?;");
            ps.setLong(1, crafter.getWurmId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                tools.add(rs.getLong(1));
            }
        });

        return tools;
    }

    @SuppressWarnings("SqlResolve")
    public static void addGivenToolFor(Creature crafter, Item tool) throws SQLException {
        execute(db -> {
            PreparedStatement ps = db.prepareStatement("INSERT INTO donated_tools VALUES(?, ?);");
            ps.setLong(1, crafter.getWurmId());
            ps.setLong(2, tool.getWurmId());
            ps.executeUpdate();
        });
    }

    @SuppressWarnings("SqlResolve")
    public static void removeGivenToolFor(Creature crafter, Item tool) throws SQLException {
        execute(db -> {
            PreparedStatement ps = db.prepareStatement("DELETE FROM donated_tools WHERE crafter_id=? AND item_id=?;");
            ps.setLong(1, crafter.getWurmId());
            ps.setLong(2, tool.getWurmId());
            ps.executeUpdate();
        });
    }
}
