package fi.thl.covid19.publishtoken.generation.v1;

import java.time.Instant;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class PublishToken {
    public final String token;
    public final Instant createTime;
    public final Instant validThroughTime;

    public PublishToken(String token, Instant createTime, Instant validThroughTime) {
        this.token = requireNonNull(token);
        this.createTime = requireNonNull(createTime);
        this.validThroughTime = requireNonNull(validThroughTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublishToken that = (PublishToken) o;
        return token.equals(that.token) &&
                createTime.equals(that.createTime) &&
                validThroughTime.equals(that.validThroughTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, createTime, validThroughTime);
    }
}
