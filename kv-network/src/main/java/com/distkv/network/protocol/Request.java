package com.distkv.network.protocol;

import java.util.Collections;
import java.util.List;

/**
 * Represents a parsed client request containing a command and arguments.
 */
public class Request {

    private final Command command;
    private final List<String> args;

    public Request(Command command, List<String> args) {
        this.command = command;
        this.args = args != null ? Collections.unmodifiableList(args) : Collections.emptyList();
    }

    public Command getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getArg(int index) {
        return (index >= 0 && index < args.size()) ? args.get(index) : null;
    }

    public int argCount() {
        return args.size();
    }

    @Override
    public String toString() {
        return "Request{command=" + command + ", args=" + args + '}';
    }
}
