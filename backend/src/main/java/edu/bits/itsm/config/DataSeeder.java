package edu.bits.itsm.config;

import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.sla.SLAPolicy;
import edu.bits.itsm.domain.user.Administrator;
import edu.bits.itsm.domain.user.Employee;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;

/**
 * Seeds reference data (categories, services, SLA policies) and demo accounts on an empty database.
 * Demo logins (all passwords "password123"):
 *   admin / tech.arjun / tech.priya / tech.rahul / tech.sneha / emp.raj / emp.meera
 */
@Configuration
@ConditionalOnProperty(name = "itsm.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "password123";

    @Bean
    CommandLineRunner seed(UserRepository users, IncidentCategoryRepository categories, ITServiceRepository services,
                           SLAPolicyRepository slas, PasswordEncoder enc) {
        return args -> {
            if (users.count() > 0) { log.info("Database already seeded; skipping"); return; }
            log.info("Seeding reference data and demo accounts");

            Map<String, IncidentCategory> cat = new java.util.HashMap<>();
            record C(String name, String desc, String kw) { }
            for (C c : new C[]{
                    new C("HARDWARE", "Physical devices", "laptop,printer,monitor,keyboard,mouse,screen,hardware,device,battery,charger,docking"),
                    new C("SOFTWARE", "Installed applications", "software,install,crash,update,license,excel,word,office,application crash,bug"),
                    new C("NETWORK", "Connectivity", "network,wifi,wi-fi,vpn,internet,lan,connection,connectivity,dns,slow network,ethernet"),
                    new C("DATABASE", "Database services", "database,db,sql,query,oracle,postgres,mysql,table,deadlock,replication"),
                    new C("CLOUD", "Cloud platforms", "cloud,aws,azure,gcp,s3,bucket,kubernetes,container,instance"),
                    new C("SECURITY", "Security incidents", "security,phishing,malware,virus,breach,suspicious,ransomware,firewall,antivirus"),
                    new C("AUTHENTICATION", "Login / identity", "login,password,sso,otp,mfa,2fa,locked,account locked,sign in,authentication,reset password"),
                    new C("APPLICATION", "Business applications", "payment,transaction,erp,crm,hrms,portal,checkout,invoice,app unavailable,500 error,timeout"),
                    new C("STORAGE", "File & object storage", "storage,disk,file share,drive,quota,backup,restore,full disk,nas"),
                    new C("EMAIL", "Messaging", "email,outlook,mail,mailbox,smtp,attachment,calendar,teams,slack"),
                    new C("OTHER", "Uncategorised", "")}) {
                cat.put(c.name(), categories.save(new IncidentCategory(c.name(), c.desc(), c.kw())));
            }

            services.save(new ITService("Payment Application", "Customer payment & checkout", BusinessCriticality.MISSION_CRITICAL, "Finance IT", 5000));
            services.save(new ITService("Customer Database", "Primary PostgreSQL cluster", BusinessCriticality.MISSION_CRITICAL, "Data Platform", 800));
            services.save(new ITService("Corporate Email", "Exchange / Outlook", BusinessCriticality.HIGH, "Workplace IT", 3000));
            services.save(new ITService("VPN", "Remote access gateway", BusinessCriticality.HIGH, "Network Ops", 1500));
            services.save(new ITService("Single Sign-On", "Identity provider", BusinessCriticality.MISSION_CRITICAL, "Security", 3200));
            services.save(new ITService("HR Portal", "Leave, payroll, self-service", BusinessCriticality.MEDIUM, "HR Systems", 2500));
            services.save(new ITService("Cloud Storage", "Shared drives & object storage", BusinessCriticality.MEDIUM, "Cloud Team", 2800));
            services.save(new ITService("Office Wi-Fi", "Campus wireless", BusinessCriticality.MEDIUM, "Network Ops", 3000));
            services.save(new ITService("Printing", "Floor printers", BusinessCriticality.LOW, "Workplace IT", 1200));
            services.save(new ITService("Intranet", "Internal news & wiki", BusinessCriticality.LOW, "Comms", 3000));

            slas.save(new SLAPolicy(Priority.P1, 5, 30));
            slas.save(new SLAPolicy(Priority.P2, 15, 120));
            slas.save(new SLAPolicy(Priority.P3, 60, 480));
            slas.save(new SLAPolicy(Priority.P4, 240, 1440));

            String pw = enc.encode(DEMO_PASSWORD);
            users.save(new Administrator("admin", pw, "Anita Desai", "admin@corp.example", "+91-9000000001"));

            Technician arjun = new Technician("tech.arjun", pw, "Arjun Mehta", "arjun@corp.example", "+91-9000000011", 8, 6);
            arjun.setSkills(Set.of(cat.get("APPLICATION"), cat.get("DATABASE"), cat.get("CLOUD"))); arjun.setTeamLead(true);
            Technician priya = new Technician("tech.priya", pw, "Priya Nair", "priya@corp.example", "+91-9000000012", 5, 5);
            priya.setSkills(Set.of(cat.get("NETWORK"), cat.get("SECURITY"), cat.get("AUTHENTICATION")));
            Technician rahul = new Technician("tech.rahul", pw, "Rahul Verma", "rahul@corp.example", "+91-9000000013", 2, 4);
            rahul.setSkills(Set.of(cat.get("HARDWARE"), cat.get("SOFTWARE"), cat.get("EMAIL"), cat.get("OTHER")));
            Technician sneha = new Technician("tech.sneha", pw, "Sneha Iyer", "sneha@corp.example", "+91-9000000014", 4, 5);
            sneha.setSkills(Set.of(cat.get("STORAGE"), cat.get("CLOUD"), cat.get("DATABASE"), cat.get("APPLICATION")));
            users.saveAll(java.util.List.of(arjun, priya, rahul, sneha));

            users.save(new Employee("emp.raj", pw, "Raj Bhatia", "raj@corp.example", "+91-9000000021", "Finance"));
            users.save(new Employee("emp.meera", pw, "Meera Krishnan", "meera@corp.example", "+91-9000000022", "Sales"));
            log.info("Seed complete. Demo password for all accounts: {}", DEMO_PASSWORD);
        };
    }
}
