package com.larpsmp.moneyevent.combat;

/**
 * How the last credited attack damaged the victim.
 */
public enum AttributionCause {
    MELEE,
    PROJECTILE,
    PET,
    EXPLOSION,
    LAVA,
    FIRE,
    TRAP,
    FALL_AFTER_HIT,
    OTHER;

    public String evidenceLabel() {
        return switch (this) {
            case MELEE -> "melee";
            case PROJECTILE -> "projectile";
            case PET -> "pet";
            case EXPLOSION -> "explosion";
            case LAVA -> "lava";
            case FIRE -> "fire";
            case TRAP -> "trap";
            case FALL_AFTER_HIT -> "fall after hit";
            case OTHER -> "attack";
        };
    }
}
