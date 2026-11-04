package edu.bits.itsm.domain.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

/** Value object: reporter's satisfaction rating after resolution. */
@Embeddable
public class Feedback {

    @Column(name = "feedback_rating")
    private Integer rating;

    @Column(name = "feedback_comment", length = 1000)
    private String comment;

    @Column(name = "feedback_at")
    private LocalDateTime givenAt;

    protected Feedback() { }

    public Feedback(int rating, String comment, LocalDateTime givenAt) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5");
        this.rating = rating;
        this.comment = comment;
        this.givenAt = givenAt;
    }

    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getGivenAt() { return givenAt; }
    public boolean isPresent() { return rating != null; }
}
