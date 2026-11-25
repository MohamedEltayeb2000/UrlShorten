package alt;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderManager manager;

    public OrderController(OrderManager manager) {
        this.manager = manager;
    }

    @RequestMapping(value = "/place", method = RequestMethod.POST)
    public String place(String ref, int total) {
        return manager.place(ref, total);
    }
}
