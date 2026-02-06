package gitlet;

import java.util.Objects;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Chen
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                validateNumArgs(args, 1);
                Repository.init();
                break;
            case "add":
                checkInit();
                validateNumArgs(args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                checkInit();
                validateNumArgs(args, 2);
                Repository.commit(args[1], null);
                break;
            case "rm":
                checkInit();
                validateNumArgs(args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                checkInit();
                validateNumArgs(args, 1);
                Repository.log();
                break;
            case "global-log":
                checkInit();
                validateNumArgs(args, 1);
                Repository.globalLog();
                break;
            case "find":
                checkInit();
                validateNumArgs(args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                checkInit();
                validateNumArgs(args, 1);
                Repository.status();
                break;
            case "checkout":
                checkInit();
                if (args.length == 2) {         /// [branch name]
                    Repository.checkOutBranch(args[1]);
                } else if (args.length == 3) {  /// -- [file name]
                    if (!Objects.equals(args[1], "--")) {
                        throwError("Incorrect operands.");
                    }
                    Repository.checkOutFile(args[2]);
                } else if (args.length == 4) {  /// [commit id] -- [file name]
                    if (!Objects.equals(args[2], "--")) {
                        throwError("Incorrect operands.");
                    }
                    Repository.checkOutCommit(args[1], args[3]);
                }
                break;
            case "branch":
                checkInit();
                validateNumArgs(args, 2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                checkInit();
                validateNumArgs(args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                checkInit();
                validateNumArgs(args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                checkInit();
                validateNumArgs(args, 2);
                Repository.merge(args[1]);
                break;
            case "graph-log":
                checkInit();
                validateNumArgs(args, 1);
                Repository.graphLog();
                break;
            case "add-remote":
                checkInit();
                validateNumArgs(args, 3);
                Repository.addRemote(args[1], args[2]);
                break;
            case "rm-remote":
                checkInit();
                validateNumArgs(args, 2);
                Repository.removeRemote(args[1]);
                break;
            case "push":
                checkInit();
                validateNumArgs(args, 3);
                Repository.push(args[1], args[2]);
                break;
            case "fetch":
                checkInit();
                validateNumArgs(args, 3);
                Repository.fetch(args[1], args[2]);
                break;
            case "pull":
                checkInit();
                validateNumArgs(args, 3);
                Repository.pull(args[1], args[2]);
                break;
            default:
                throwError("No command with that name exists.");
                break;
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     * print out error message if they do not match.
     *
     * @param args Argument array passed in from command line
     * @param n Number of expected arguments
     */
    private static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            throwError("Incorrect operands.");
        }
    }

    /**
     * Checks if this directory is correctly initialized.
     */
    private static void checkInit() {
        if (!Repository.GITLET_DIR.exists()) {
            throwError("Not in an initialized Gitlet directory.");
        }
    }

    /**
     * Print out error message and exit program.
     */
    private static void throwError(String message) {
        System.out.println(message);
        System.exit(0);
    }
}
