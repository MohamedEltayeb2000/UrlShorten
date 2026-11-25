package alt;

@Service
public class OrderManager {

    @Autowired
    private OrderDao dao;

    public String place(String ref, int total) {
        if (dao.countByRef(ref) > 0) {
            return ref;
        }
        dao.save(ref, total);
        return ref;
    }
}
