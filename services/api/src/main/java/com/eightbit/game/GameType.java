package com.eightbit.game;

import jakarta.persistence.*;

@Entity
@Table(name = "game_types")
public class GameType {

    @Id
    @Column(length = 20)
    private String code;            // 'wordle', 'connections', ...

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    protected GameType() {}

    public GameType(String code, String displayName, boolean active) {
        this.code = code;
        this.displayName = displayName;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
