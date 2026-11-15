package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.exception.InvalidAiRequestException;
import edu.bits.itsm.web.dto.AiRequests.ChatMessage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Feature 2: chatbot reporting. Claude asks one short follow-up question at a time (at most {@value #MAX_QUESTIONS}),
 * then returns a draft incident. The chat never creates the incident: the employee confirms in the UI, which calls the
 * normal create endpoint. Java enforces the question limit and validates the draft.
 * Rules fallback: one reply pointing to the normal form, {@code done = true}, no draft.
 */
@Service
public class ChatReportingService {

    public static final int MAX_QUESTIONS = 3;
    public static final int MAX_MESSAGES = 12;
    static final int REPLY_MAX = 500;
    static final String RULES_REPLY = "The chat assistant is not available right now. Please use the normal Report incident form instead.";
    static final String DONE_REPLY = "Thanks, that is everything I need. Please check the draft and submit it if it looks right.";

    private static final Map<String, Object> SCHEMA = OutputSchema.object(
            "done", OutputSchema.bool("true when you have enough to fill the draft, or after the last allowed question"),
            "reply", OutputSchema.string("Your one short question, or a short closing line when done"),
            "draft", OutputSchema.nullable(IncidentDraft.schema()));

    private final AiModeService ai;
    private final LiveCatalog catalog;

    public ChatReportingService(AiModeService ai, LiveCatalog catalog) {
        this.ai = ai;
        this.catalog = catalog;
    }

    /** Body of {@code POST /api/ai/chat}. {@code draft} is set only when {@code done}. */
    public record Result(TextSource source, String reply, boolean done, IncidentDraft draft) {
        static Result rules() { return new Result(TextSource.RULES, RULES_REPLY, true, null); }
    }

    /** @throws InvalidAiRequestException when the conversation is malformed, in every mode */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public Result reply(List<ChatMessage> messages) {
        List<AiMessage> turns = conversation(messages);
        if (!ai.isLive()) return Result.rules();
        int asked = (int) turns.stream().filter(t -> t.role() == AiMessage.Role.ASSISTANT).count();
        LiveCatalog.Snapshot live = catalog.snapshot();
        return ai.call(request(turns, asked, live)).flatMap(answer -> read(answer, asked, turns, live)).orElseGet(Result::rules);
    }

    /**
     * Checks the shape the API needs: 1-12 turns with roles user/assistant, ending with the employee. Assistant turns
     * before the employee's first message (a greeting shown by the UI) are dropped and do not count as questions.
     */
    static List<AiMessage> conversation(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > MAX_MESSAGES)
            throw new InvalidAiRequestException("A chat needs between 1 and " + MAX_MESSAGES + " messages");
        List<AiMessage> turns = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) throw new InvalidAiRequestException("Chat messages cannot be empty");
            if ("user".equals(m.role())) turns.add(AiMessage.user(m.content().strip()));
            else if (!"assistant".equals(m.role())) throw new InvalidAiRequestException("A chat message role must be user or assistant");
            else if (!turns.isEmpty()) turns.add(AiMessage.assistant(m.content().strip()));
        }
        if (turns.isEmpty() || turns.get(turns.size() - 1).role() != AiMessage.Role.USER)
            throw new InvalidAiRequestException("A chat must end with a message from the user");
        return turns;
    }

    private static AiRequest request(List<AiMessage> turns, int asked, LiveCatalog.Snapshot live) {
        String system = """
                You are the I-ITSM reporting assistant. Help an employee describe one IT problem so that a service-desk \
                incident can be drafted.
                - Ask one short, friendly question at a time, and only when the answer would change the draft: which \
                service, what exactly fails, how many people are affected, how urgent it is.
                - You may ask at most %d questions in total. You have asked %d so far.%s
                - When you know enough, set done to true, write a short closing line in reply and fill the draft. While \
                done is false, draft must be null.
                - In the draft use only service and category ids from the catalog below, or null when unsure.
                """.formatted(MAX_QUESTIONS, asked, asked >= MAX_QUESTIONS ? " You must finish now: set done to true." : "")
                + Prompts.DATA_NOT_INSTRUCTIONS + "\n" + Prompts.PROPOSE_ONLY + "\n\n" + live.servicesAndCategories();
        return new AiRequest(AiFeature.CHAT, system, turns, SCHEMA);
    }

    /** Applies the rules on top of the model: the question limit, a validated draft, and no empty question. */
    static Optional<Result> read(JsonNode answer, int asked, List<AiMessage> turns, LiveCatalog.Snapshot live) {
        boolean modelDone = ModelOutput.bool(answer, "done");
        String reply = ModelOutput.text(answer, "reply", REPLY_MAX);
        if (!modelDone && asked < MAX_QUESTIONS)
            return reply == null ? Optional.empty() : Optional.of(new Result(TextSource.AI, reply, false, null));
        boolean forced = !modelDone;   // the question budget is spent: finish even though the model wanted to ask more
        List<String> employeeWords = turns.stream().filter(t -> t.role() == AiMessage.Role.USER).map(AiMessage::text).toList();
        IncidentDraft draft = IncidentDraft.fromModel(answer.get("draft"), live).withTextFrom(employeeWords);
        return Optional.of(new Result(TextSource.AI, forced || reply == null ? DONE_REPLY : reply, true, draft));
    }
}
