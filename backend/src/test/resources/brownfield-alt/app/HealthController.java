package alt;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
