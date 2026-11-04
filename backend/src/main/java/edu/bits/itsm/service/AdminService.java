package edu.bits.itsm.service;

import edu.bits.itsm.config.StrategyRegistry;
import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.exception.ConflictException;
import edu.bits.itsm.exception.ResourceNotFoundException;
import edu.bits.itsm.repository.*;
import edu.bits.itsm.web.dto.AdminRequests.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Administrator operations: users, technicians, services, categories, strategies. */
@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final UserFactory userFactory;
    private final StrategyRegistry strategies;

    public AdminService(UserRepository users, TechnicianRepository technicians, ITServiceRepository services,
                        IncidentCategoryRepository categories, UserFactory userFactory, StrategyRegistry strategies) {
        this.users = users; this.technicians = technicians; this.services = services;
        this.categories = categories; this.userFactory = userFactory; this.strategies = strategies;
    }

    // users
    public List<User> allUsers() { return users.findAll(); }

    public User createUser(CreateUser r) {
        if (users.existsByUsername(r.username())) throw new ConflictException("Username already taken");
        if (users.existsByEmail(r.email())) throw new ConflictException("Email already registered");
        return users.save(userFactory.create(r, resolveSkills(r.skillCategoryIds())));
    }

    public User setActive(Long id, boolean active) {
        User u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (active) u.activate(); else u.deactivate();
        return u;
    }

    // technicians
    public List<Technician> technicians() { return technicians.findAll(); }

    public Technician updateTechnician(Long id, UpdateTechnician r) {
        Technician t = technicians.findById(id).orElseThrow(() -> new ResourceNotFoundException("Technician", id));
        if (r.skillCategoryIds() != null) t.setSkills(resolveSkills(r.skillCategoryIds()));
        if (r.experienceYears() != null) t.setExperienceYears(r.experienceYears());
        if (r.maxWorkload() != null) t.setMaxWorkload(r.maxWorkload());
        if (r.available() != null) t.setAvailable(r.available());
        if (r.teamLead() != null) t.setTeamLead(r.teamLead());
        return t;
    }

    private Set<IncidentCategory> resolveSkills(Set<Long> ids) {
        Set<IncidentCategory> out = new HashSet<>();
        if (ids != null) for (Long id : ids) out.add(categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id)));
        return out;
    }

    // services
    public ITService createService(ServiceReq r) {
        services.findByNameIgnoreCase(r.name()).ifPresent(s -> { throw new ConflictException("Service already exists: " + r.name()); });
        return services.save(new ITService(r.name(), r.description(), r.criticality(), r.owner(), r.userBase() == null ? 0 : r.userBase()));
    }

    public ITService updateService(Long id, ServiceReq r) {
        ITService s = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
        s.setName(r.name()); s.setDescription(r.description()); s.setCriticality(r.criticality()); s.setOwner(r.owner());
        if (r.userBase() != null) s.setUserBase(r.userBase());
        if (r.active() != null) s.setActive(r.active());
        return s;
    }

    // categories
    public IncidentCategory createCategory(CategoryReq r) {
        categories.findByName(r.name().trim().toUpperCase()).ifPresent(c -> { throw new ConflictException("Category already exists: " + r.name()); });
        return categories.save(new IncidentCategory(r.name(), r.description(), r.keywords()));
    }

    public IncidentCategory updateCategory(Long id, CategoryReq r) {
        IncidentCategory c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        c.setDescription(r.description()); c.setKeywords(r.keywords());
        if (r.active() != null) c.setActive(r.active());
        return c;
    }

    // strategies
    public void configureStrategies(StrategyReq r) {
        if (r.assignmentStrategy() != null) strategies.activateAssignment(r.assignmentStrategy());
        if (r.priorityStrategy() != null) strategies.activatePriority(r.priorityStrategy());
    }
}
