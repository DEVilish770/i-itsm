package edu.bits.itsm.ai.feature;

import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.repository.ITServiceRepository;
import edu.bits.itsm.repository.IncidentCategoryRepository;
import edu.bits.itsm.repository.TechnicianRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The live catalogue a model may choose ids from: active services and categories (plus active technicians, for search).
 * A {@link Snapshot} is taken once per request and used twice: rendered into the prompt, then to check every id that
 * comes back, so an id the model invents (or one deactivated meanwhile) becomes null.
 */
@Component
public class LiveCatalog {

    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final TechnicianRepository technicians;

    public LiveCatalog(ITServiceRepository services, IncidentCategoryRepository categories, TechnicianRepository technicians) {
        this.services = services;
        this.categories = categories;
        this.technicians = technicians;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                entries(services.findByActiveTrue(), s -> new Entry(s.getId(), s.getName(), s.getDescription()), ITService::getId),
                entries(categories.findByActiveTrue(), c -> new Entry(c.getId(), c.getName(), c.getDescription()), IncidentCategory::getId),
                entries(technicians.findByActiveTrue(), t -> new Entry(t.getId(), t.getFullName(), null), Technician::getId));
    }

    private static <T> List<Entry> entries(List<T> items, Function<T, Entry> toEntry, Function<T, Long> id) {
        return items.stream().sorted(Comparator.comparing(id)).map(toEntry).toList();
    }

    /** One choosable item: its id, name and an optional hint for the model. */
    public record Entry(Long id, String name, String description) {
        String line() { return id + ": " + name + (description == null || description.isBlank() ? "" : " - " + description); }
    }

    /** The catalogue at one moment. */
    public record Snapshot(List<Entry> services, List<Entry> categories, List<Entry> technicians) {

        public boolean hasService(Long id) { return find(services, id) != null; }
        public boolean hasCategory(Long id) { return find(categories, id) != null; }
        public boolean hasTechnician(Long id) { return find(technicians, id) != null; }

        public String serviceName(Long id) { return name(services, id); }
        public String categoryName(Long id) { return name(categories, id); }
        public String technicianName(Long id) { return name(technicians, id); }

        /** Prompt text listing services and categories as "id: name - description" lines. */
        public String servicesAndCategories() {
            return "Services (id: name - description):\n" + lines(services) + "\n\nCategories (id: name - description):\n" + lines(categories);
        }

        /** Prompt text listing technicians as "id: name" lines. */
        public String technicianList() { return "Technicians (id: name):\n" + lines(technicians); }

        private static String lines(List<Entry> entries) {
            return entries.isEmpty() ? "(none)" : entries.stream().map(Entry::line).collect(Collectors.joining("\n"));
        }

        private static Entry find(List<Entry> entries, Long id) {
            return id == null ? null : entries.stream().filter(e -> Objects.equals(e.id(), id)).findFirst().orElse(null);
        }

        private static String name(List<Entry> entries, Long id) {
            Entry e = find(entries, id);
            return e == null ? null : e.name();
        }
    }
}
