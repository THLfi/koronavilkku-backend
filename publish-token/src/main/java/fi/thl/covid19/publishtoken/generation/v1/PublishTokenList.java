package fi.thl.covid19.publishtoken.generation.v1;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class PublishTokenList {
    public final List<PublishToken> publishTokens;

    public PublishTokenList(List<PublishToken> publishTokens) {
        this.publishTokens = requireNonNull(publishTokens);
    }
}
