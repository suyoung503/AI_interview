package com.interview;

import com.interview.dto.InterviewAiModels.Clarity;
import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.dto.InterviewAiModels.Reasoning;
import com.interview.dto.InterviewAiModels.TechnicalAccuracy;
import com.interview.dto.SessionResponse;
import com.interview.dto.StatsResponse;
import com.interview.entity.Answer;
import com.interview.entity.InterviewJsonConverters.EvaluationConverter;
import com.interview.dto.InterviewAiModels.Axis;
import com.interview.dto.InterviewAiModels.AxisAssessment;
import com.interview.dto.InterviewAiModels.AnswerEvidence;
import com.interview.dto.InterviewAiModels.EvidenceSource;
import com.interview.entity.Category;
import com.interview.entity.Question;
import com.interview.entity.Resume;
import com.interview.entity.Session;
import com.interview.entity.User;
import com.interview.repository.AnswerRepository;
import com.interview.repository.CategoryRepository;
import com.interview.repository.QuestionRepository;
import com.interview.repository.ResumeRepository;
import com.interview.repository.SessionRepository;
import com.interview.repository.UserRepository;
import com.interview.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class InterviewStorageTest {

    private static final String SOURCE = "Redis 캐시를 도입해 API 지연 개선 🚀";
    private static final String RESUME_TEXT = "  팀 프로젝트\r\n" + SOURCE + "\n";
    private static final String FOLLOW_UP = "어떤 지표로 판단했나요?";
    private static final Evaluation INITIAL = new Evaluation(
            TechnicalAccuracy.NOT_ASSESSABLE, Reasoning.INSUFFICIENT, Clarity.CLEAR,
            List.of("선택한 기술 명시"), List.of("비교 기준과 지표 추가"), List.of("실제 성능 수치"),
            "선택 이유는 확인이 더 필요합니다.", FOLLOW_UP
    );
    private static final Evaluation COMBINED = new Evaluation(
            TechnicalAccuracy.CORRECT, Reasoning.SUFFICIENT, Clarity.CLEAR,
            List.of("캐시 적중률 설명", "P95 지연 지표"), List.of(), List.of("수치는 독립적으로 확인하지 않음"),
            "선택 이유와 판단 기준을 설명했습니다.", null
    );

    @Autowired private UserRepository users;
    @Autowired private ResumeRepository resumes;
    @Autowired private SessionRepository sessions;
    @Autowired private CategoryRepository categories;
    @Autowired private QuestionRepository questions;
    @Autowired private AnswerRepository answers;
    @Autowired private StatsService statsService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void preservesLegacyEightFieldJsonWithoutAddingNewFields() {
        var converter = new EvaluationConverter();
        String original = converter.convertToDatabaseColumn(INITIAL);
        assertThat(original).doesNotContain("assessments", "followUpAxis");
        Evaluation restored = converter.convertToEntityAttribute(original);
        assertThat(restored).isEqualTo(INITIAL);
        assertThat(restored.assessments()).isNull();
        assertThat(restored.followUpAxis()).isNull();
        assertThat(converter.convertToDatabaseColumn(restored)).isEqualTo(original);
    }

    @Test
    void persistsGroundedAssessmentsWithoutChangingLegacyRows() {
        var assessments = List.of(new AxisAssessment(Axis.REASONING,
                List.of(new AnswerEvidence(EvidenceSource.ORIGINAL_ANSWER, "익숙한 기술이었습니다.")),
                List.of("선택 제약 설명"), List.of("요구사항과 연결")));
        var current = new Evaluation(INITIAL.technicalAccuracy(), INITIAL.reasoning(), INITIAL.clarity(),
                INITIAL.strengths(), INITIAL.improvements(), INITIAL.uncertainties(), INITIAL.summary(),
                INITIAL.followUpQuestion(), assessments, Axis.REASONING);
        var transactions = new TransactionTemplate(transactionManager);
        Long id = transactions.execute(status -> {
            var session = newResumeSession(newUser());
            var question = questions.save(Question.root(session, 1, generatedQuestion()));
            return answers.save(Answer.evaluated(question, "익숙한 기술이었습니다.", current,
                    "test-model", "resume-interview-v5")).getId();
        });
        transactions.executeWithoutResult(status -> assertThat(answers.findById(id).orElseThrow().getEvaluation()).isEqualTo(current));
        assertThatThrownBy(() -> current.assessments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> current.assessments().get(0).evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reloadsResumeOrderedQuestionsAndBothEvaluationsInANewTransaction() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long sessionId = transactions.execute(status -> {
            Session session = newResumeSession(newUser());
            List<String> criteria = new ArrayList<>(List.of("요구사항 적합성", "비교 \"기준\"\n설명"));
            GeneratedQuestion question = new GeneratedQuestion(
                    "Redis를 선택한 이유는 무엇인가요?", SOURCE, "선택 근거 확인", criteria);
            criteria.clear();
            questions.save(Question.root(session, 2, generatedQuestion()));
            Question root = questions.save(Question.root(session, 1, question));
            Question child = questions.save(Question.followUp(session, root, FOLLOW_UP));
            answers.save(Answer.evaluated(root, "익숙한 기술이었습니다.", INITIAL, "test-eval-v1", "rubric-1"));
            answers.save(Answer.evaluated(child, "적중률과 P95를 봤습니다.", COMBINED, "test-eval-v2", "rubric-2"));
            return session.getId();
        });

        transactions.executeWithoutResult(status -> {
            Session session = sessions.findById(sessionId).orElseThrow();
            assertThat(session.getResume().getText()).isEqualTo(RESUME_TEXT);
            assertThat(session.getResume().getUser().getId()).isEqualTo(session.getUser().getId());
            assertThat(session.getModel()).isEqualTo("test-generator");
            assertThat(session.getCriteriaVersion()).isEqualTo("question-rubric-1");
            assertThat(new SessionResponse(session).getCategoryName()).isNull();

            List<Question> stored = questions.findHistoryBySession(session).stream()
                    .map(QuestionRepository.HistoryRow::getQuestion)
                    .toList();
            assertThat(stored).extracting(Question::getContent, Question::getPosition)
                    .containsExactly(tuple("Redis를 선택한 이유는 무엇인가요?", 1), tuple(FOLLOW_UP, 1),
                            tuple("캐시를 언제 무효화했나요?", 2));
            Question root = stored.get(0);
            Question child = stored.get(1);
            assertThat(root.getParentQuestion()).isNull();
            assertThat(child.getParentQuestion().getId()).isEqualTo(root.getId());
            for (Question question : List.of(root, child)) {
                assertThat(question.getSession().getId()).isEqualTo(sessionId);
                assertThat(question.getCategory()).isNull();
                assertThat(question.getSourceQuote()).isEqualTo(SOURCE);
                assertThat(question.getIntent()).isEqualTo("선택 근거 확인");
                assertThat(question.getEvaluationCriteria()).containsExactly("요구사항 적합성", "비교 \"기준\"\n설명");
            }

            assertThat(answers.findBySession(session))
                    .extracting(answer -> answer.getQuestion().getId(), Answer::getUserAnswer, Answer::getEvaluation,
                            Answer::getModel, Answer::getCriteriaVersion, Answer::getScore)
                    .containsExactlyInAnyOrder(
                            tuple(root.getId(), "익숙한 기술이었습니다.", INITIAL, "test-eval-v1", "rubric-1", null),
                            tuple(child.getId(), "적중률과 P95를 봤습니다.", COMBINED, "test-eval-v2", "rubric-2", null));
        });
    }

    @Test
    void rejectsQuestionsOutsideTheSameResumeSessionAsFollowUpParents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = newUser();
            Session session = newResumeSession(user);
            Session anotherSession = sessions.save(Session.forResume(session.getResume(), "test-generator", "rubric"));
            Question root = questions.save(Question.root(session, 1, generatedQuestion()));
            Question child = questions.save(Question.followUp(session, root, FOLLOW_UP));
            Category category = newCategory();
            Question fixed = newFixedQuestion(category);
            Session legacy = sessions.save(Session.builder().user(user).category(category).build());

            assertThatThrownBy(() -> Question.followUp(anotherSession, root, FOLLOW_UP))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Question.followUp(session, child, FOLLOW_UP))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Question.followUp(session, fixed, FOLLOW_UP))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Question.root(legacy, 1, generatedQuestion()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Question.root(null, 1, generatedQuestion()))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void preservesDuplicateLegacyAnswersAndExcludesUnscoredAnswersFromAverages() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long legacyId = transactions.execute(status -> {
            User user = newUser();
            Category category = newCategory();
            Question fixed = newFixedQuestion(category);
            Session legacy = sessions.save(Session.builder().user(user).category(category).build());
            legacy.done();
            answers.save(Answer.builder().session(legacy).question(fixed).userAnswer("기존 답변 A")
                    .feedback("기존 평가 A").score(7).build());
            answers.save(Answer.builder().session(legacy).question(fixed).userAnswer("기존 답변 B")
                    .feedback("기존 평가 B").score(9).build());

            Session current = newResumeSession(user);
            Question root = questions.save(Question.root(current, 1, generatedQuestion()));
            answers.save(Answer.evaluated(root, "신규 답변", COMBINED, "test-eval", "rubric-1"));
            return legacy.getId();
        });

        transactions.executeWithoutResult(status -> {
            Session session = sessions.findById(legacyId).orElseThrow();
            String categoryName = session.getCategory().getName();
            assertThat(new SessionResponse(session).getCategoryName()).isEqualTo(categoryName);
            assertThat(session.getResume()).isNull();
            assertThat(questions.findHistoryBySession(session)).isEmpty();
            List<Answer> past = answers.findBySession(session);
            assertThat(past).extracting(Answer::getUserAnswer, Answer::getFeedback, Answer::getScore,
                            Answer::getEvaluation)
                    .containsExactlyInAnyOrder(tuple("기존 답변 A", "기존 평가 A", 7, null),
                            tuple("기존 답변 B", "기존 평가 B", 9, null));
            for (Answer answer : past) {
                assertThat(answer.getQuestion().getContent()).isEqualTo("기존 고정 질문");
                assertThat(answer.getQuestion().getCategory().getName()).isEqualTo(categoryName);
                assertThat(answer.getQuestion().getSession()).isNull();
            }

            StatsResponse stats = statsService.getMyStats(session.getUser().getEmail());
            assertThat(stats.getTotalSessions()).isEqualTo(2);
            assertThat(stats.getCompletedSessions()).isEqualTo(1);
            assertThat(stats.getOverallAverageScore()).isEqualTo(8);
            assertThat(stats.getCategoryStats())
                    .extracting(StatsResponse.CategoryStat::getCategoryName, StatsResponse.CategoryStat::getAverageScore,
                            StatsResponse.CategoryStat::getAnswerCount)
                    .containsExactly(tuple(categoryName, 8.0, 2));
        });
    }

    @Test
    void rejectsCorruptedStructuredEvaluationInsteadOfReadingItAsMissing() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long answerId = transactions.execute(status -> {
            Session session = newResumeSession(newUser());
            Question root = questions.save(Question.root(session, 1, generatedQuestion()));
            Answer answer = answers.save(Answer.evaluated(root, "원답", COMBINED, "test-eval", "rubric-1"));
            jdbc.update("update answers set evaluation = ? where id = ?", "{invalid", answer.getId());
            return answer.getId();
        });

        assertThatThrownBy(() -> transactions.execute(status -> answers.findById(answerId)))
                .isInstanceOf(DataAccessException.class);
    }

    private User newUser() {
        return users.save(User.builder().email(UUID.randomUUID() + "@example.test").password("test-password").build());
    }

    private Session newResumeSession(User user) {
        Resume resume = resumes.save(Resume.builder().user(user).text(RESUME_TEXT).build());
        return sessions.save(Session.forResume(resume, "test-generator", "question-rubric-1"));
    }

    private GeneratedQuestion generatedQuestion() {
        return new GeneratedQuestion("캐시를 언제 무효화했나요?", SOURCE, "무효화 시점 확인", List.of("정합성"));
    }

    private Category newCategory() {
        String name = "기존 " + UUID.randomUUID();
        jdbc.update("insert into categories (name) values (?)", name);
        Long id = jdbc.queryForObject("select id from categories where name = ?", Long.class, name);
        return categories.findById(id).orElseThrow();
    }

    private Question newFixedQuestion(Category category) {
        jdbc.update("insert into questions (category_id, content) values (?, ?)", category.getId(), "기존 고정 질문");
        return questions.findByCategory(category).get(0);
    }
}
