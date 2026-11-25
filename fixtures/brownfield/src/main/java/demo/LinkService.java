package demo;

import java.security.SecureRandom;

/** Business logic for short links. */
@Service
public class LinkService {

    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private final LinkRepository repository;
    private final SecureRandom random = new SecureRandom();

    public LinkService(LinkRepository repository) {
        this.repository = repository;
    }

    /** Creates a link. Every call inserts a new row, so a retried request creates a duplicate. */
    public Link createLink(String targetUrl) {
        String code = generateCode();
        Link link = new Link(code, targetUrl);
        repository.insert(link);
        return link;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
