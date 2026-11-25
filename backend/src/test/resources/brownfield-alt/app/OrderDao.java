package alt;

@Repository
public class OrderDao {

    public int countByRef(String ref) {
        return query("SELECT COUNT(*) FROM purchase_orders WHERE ref = ?", ref);
    }

    public void save(String ref, int total) {
        run("INSERT INTO purchase_orders (ref, total) VALUES (?, ?)", ref, total);
    }
}
