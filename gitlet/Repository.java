package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *  Encapsulates the repository's on-disk state
 *  (e.g., .gitlet directory structure, commits, staging area),
 *  and implements the logic for Gitlet commands like init, add and commit.
 *
 *  @author Chen
 */
public class Repository {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** Directories. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File STAGING_DIR = join(GITLET_DIR, "staging");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");
    public static final File REFS_REMOTES_DIR = join(REFS_DIR, "remotes");
    public static final File ADD_DIR = join(STAGING_DIR, "add");
    public static final File REMOVE_DIR = join(STAGING_DIR, "remove");
    public static final File REMOTE_DIR = join(GITLET_DIR, "remote");

    /** Files. */
    public static final File MASTER_FILE =  join(HEADS_DIR, "master");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");


    /**
     * Initialize the persistence system and pointers for gitlet.
     * A .gitlet folder will be generated, inside which 
     * has the persistence structure, as described in design document.
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system "
                    + "already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        STAGING_DIR.mkdir();
        REFS_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        HEADS_DIR.mkdir();
        REFS_REMOTES_DIR.mkdir();
        ADD_DIR.mkdir();
        REMOVE_DIR.mkdir();
        REMOTE_DIR.mkdir();

        // Create initial commit, and serialize it.
        Commit initialCommit = new Commit("initial commit");
        initialCommit.save();

        // Write initial commit id into master pointer.
        String id = initialCommit.getId();
        try {
            MASTER_FILE.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(MASTER_FILE, id);

        // Set HEAD pointer (point to master)
        try {
            HEAD_FILE.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(HEAD_FILE, "master");
    }

    /**
     * Add ONE file into the staging area.
     * Specifically form the blob file in blob folder, 
     * check if it already exists in the staging area,
     * and delete it if it's in remove area.
     *
     * @param fileName The file to add
     */
    public static void add(String fileName) {
        final File addFile = join(CWD, fileName);

        // Check if the file exists.
        if (!addFile.exists()) {
            quit("File does not exist.");
        }

        // Generate the SHA-1 hash.
        String blobId = generateHash(addFile);

        // Form the blob file and fill in the content
        final File blobFile = join(BLOBS_DIR, blobId);
        if (!blobFile.exists()) {
            try {
                blobFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(blobFile, readContentsAsString(addFile));
        }

        // Check if current head commit is in track of this file.
        Commit headCommit = getHeadCommit();
        if (headCommit.getTrackedFiles().containsKey(fileName)) {
            // The hash of the existing old file.
            String headBlobId = headCommit.getTrackedFiles().get(fileName); 

            // If the added file is identical to that tracked by head commit, 
            // do not add it into stage area.
            if (headBlobId.equals(blobId)) {
                File existingIdenticalFileInAdd = join(ADD_DIR, fileName);
                if (existingIdenticalFileInAdd.exists()) {
                    restrictedDelete(existingIdenticalFileInAdd);
                }
            } else {  // File content is not identical, add it into stage area.
                File stageFile = join(ADD_DIR, fileName);
                try {
                    stageFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                writeContents(stageFile, blobId);
            }
        } else {  // No such file in head commit, add into stage area.
            File stageFile = join(ADD_DIR, fileName);
            try {
                stageFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(stageFile, blobId);
        }

        // Check if it is in remove area. If so, delete it.
        File removeFileWithId = join(REMOVE_DIR, fileName);
        if (removeFileWithId.exists()) {
            removeFileWithId.delete();
        }
    }

    /**
     * Saves a snapshot of tracked files in the current commit and staging area
     * so they can be restored at a later time, creating a new commit.
     *
     * @param message Message of this commit
     */
    public static void commit(String message, String secondParent) {
        // Check if the message is blank.
        if (Objects.equals(message, "")) {
            quit("Please enter a commit message.");
        }

        // Create a new commit.
        String currentBranch = readContentsAsString(HEAD_FILE);
        String parent;
        File branchRef;
        if (currentBranch.contains("/")) {
            String[] parts = currentBranch.split("/", 2);
            String remoteName = parts[0];
            String branchName = parts[1];
            branchRef = join(REFS_REMOTES_DIR, remoteName, branchName);
            parent = readContentsAsString(branchRef);
        } else {
            branchRef = join(HEADS_DIR, currentBranch);
            parent = readContentsAsString(branchRef);
        }
        Map<String, String> newTrackedFiles = new HashMap<>(getHeadCommit().getTrackedFiles());
        File[] addedFiles = ADD_DIR.listFiles(), removedFiles = REMOVE_DIR.listFiles();
        // Add files to trackFiles map.
        if ((addedFiles == null || addedFiles.length == 0)
                && (removedFiles == null || removedFiles.length == 0)) {
            quit("No changes added to the commit.");
        }
        if (addedFiles != null) {
            for (File f : addedFiles) {
                if (f.isFile()) {
                    String name = f.getName();
                    String blobId = readContentsAsString(f);
                    newTrackedFiles.put(name, blobId);
                }
            }
        }
        // Remove files in trackFiles map.
        if (removedFiles != null) {
            for (File f : removedFiles) {
                if (f.isFile()) {
                    newTrackedFiles.remove(f.getName());
                }
            }
        }
        ///  Create the new commit
        Commit thisCommit = new Commit(message, parent, secondParent, newTrackedFiles);

        // Write this commit into persistence system.
        thisCommit.save();
        writeContents(branchRef, thisCommit.getId());

        clearStaging();
    }

    /**
     * 1. Unstage the file in staging area if it is in (but do not delete it);
     * 2. Remove the file from the working directory (if still exists) and move it
     *    to remove area, when it is tracked by head commit.
     *
     * @param fileName The file to remove
     */
    public static void rm(String fileName) {
        // Situation 1
        File[] addedFiles = ADD_DIR.listFiles();
        boolean addContainsFile = false;
        if (addedFiles != null) {
            for (File f : addedFiles) {
                if (f.getName().equals(fileName)) {
                    if (!f.delete()) {
                        throw new RuntimeException("Failed to delete staging file: "
                                + f.getPath());
                    }
                    addContainsFile = true;
                    break;
                }
            }
        }

        // Situation 2
        Commit headCommit = getHeadCommit();
        boolean headContainsFile = headCommit.getTrackedFiles().containsKey(fileName);
        if (headContainsFile) {
            File removeFile = join(REMOVE_DIR, fileName);
            try {
                removeFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(removeFile, headCommit.getTrackedFiles().get(fileName));

            // Delete it if exists in working directory
            File deleteFileInWorkingDir = join(CWD, fileName);
            if (deleteFileInWorkingDir.exists()) {
                deleteFileInWorkingDir.delete();
            }
        }

        // Failure cases
        if ((!addContainsFile) && (!headContainsFile)) {
            quit("No reason to remove the file.");
        }
    }

    /**
     * Print out the commit information from the HEAD commit to init commit.
     * If one commit has two parent, print out the info, and go along the first parent.
     */
    public static void log() {
        Commit currentCommit = getHeadCommit();
        while (true) {
            printLog(currentCommit);
            if (currentCommit.getParent() == null) {
                break;
            }
            currentCommit = getCommit(currentCommit.getParent());
        }
    }

    /**
     * Print out all the commits in repository, regardless of branches they're in.
     */
    public static void globalLog() {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        assert commitIds != null;
        for (String commitId : commitIds) {
            Commit currentCommit = getCommit(commitId);
            printLog(currentCommit);
        }
    }

    /**
     * Print out the ids of all commits that have the given commit message, one per line.
     *
     * @param message The message to find
     */
    public static void find(String message) {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        if (commitIds == null) {
            quit("Found no commit with that message.");
        }
        boolean foundCommit = false;
        for (String commitId : commitIds) {
            Commit currentCommit = getCommit(commitId);
            if (Objects.equals(message, currentCommit.getMessage())) {
                System.out.println(commitId);
                foundCommit = true;
            }
        }
        if (!foundCommit) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * Displays what branches currently exist, and marks the current branch with a *.
     * Also displays what files have been staged for addition or removal.
     */
    public static void status() {
        // Print branches.
        printBranches();

        // Print staged files.
        System.out.println("=== Staged Files ===");
        List<String> stagedFiles = plainFilenamesIn(ADD_DIR);
        if (stagedFiles != null) {
            Collections.sort(stagedFiles);
            for (String stagedFile : stagedFiles) {
                System.out.println(stagedFile);
            }
        }
        System.out.println();

        // Print removed files.
        System.out.println("=== Removed Files ===");
        List<String> removedFiles = plainFilenamesIn(REMOVE_DIR);
        if (removedFiles != null) {
            Collections.sort(removedFiles);
            for (String removedFile : removedFiles) {
                System.out.println(removedFile);
            }
        }
        System.out.println();

        // Print modified but not staged files.
        printModifiedNotStagedFiles();

        // Print untracked files
        System.out.println("=== Untracked Files ===");
        List<String> printOutUntrackedFiles = new ArrayList<>(5);
        List<String> filesInWorkingDirectory = plainFilenamesIn(CWD);
        Commit headCommit = getHeadCommit();
        Map<String, String> headTrackedFiles = headCommit.getTrackedFiles();
        if (filesInWorkingDirectory != null) {
            for (String fileInWorkingDirectory : filesInWorkingDirectory) {
                if (headTrackedFiles.containsKey(fileInWorkingDirectory)) {
                    continue;
                }
                if (stagedFiles != null) {
                    if (stagedFiles.contains(fileInWorkingDirectory)) {
                        continue;
                    }
                }
                printOutUntrackedFiles.add(fileInWorkingDirectory);
            }
        }
        if (removedFiles != null) {
            for (String removedFile : removedFiles) {
                if (filesInWorkingDirectory != null) {
                    if (filesInWorkingDirectory.contains(removedFile)) {
                        printOutUntrackedFiles.add(removedFile);
                    }
                }
            }
        }
        Collections.sort(printOutUntrackedFiles);
        for (String printOutUntrackedFile : printOutUntrackedFiles) {
            System.out.println(printOutUntrackedFile);
        }
        System.out.println();
    }

    /**
     * Take the file in head commit, replace the one in working directory.
     *
     * @param fileName The file to check out
     */
    public static void checkOutFile(String fileName) {
        Commit headCommit = getHeadCommit();
        Map<String, String> trackedFiles = headCommit.getTrackedFiles();
        if (!trackedFiles.containsKey(fileName)) {
            quit("File does not exist in that commit.");
        }
        File checkoutFile = join(CWD, fileName);
        if (!checkoutFile.exists()) {
            try {
                checkoutFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String fileHash = trackedFiles.get(fileName);
        writeContents(checkoutFile, (Object) readContents(join(BLOBS_DIR, fileHash)));
    }

    /**
     * Take the version of file in the given commit, replace the one in working directory.
     *
     * @param prefix The commit version to take(allow user to input the first few digit of the id)
     * @param fileName The file to check out
     */
    public static void checkOutCommit(String prefix, String fileName) {
        Commit destinedCommit = findCorrespondingCommit(prefix);
        Map<String, String> trackedFiles = destinedCommit.getTrackedFiles();
        if (!trackedFiles.containsKey(fileName)) {
            quit("File does not exist in that commit.");
        }
        File checkoutFile = join(CWD, fileName);
        if (!checkoutFile.exists()) {
            try {
                checkoutFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String fileHash = trackedFiles.get(fileName);
        writeContents(checkoutFile, (Object) readContents(join(BLOBS_DIR, fileHash)));
    }

    /**
     * Take all files in the head commit of the new branch, put them in working directory,
     * overwrite if one exists.
     * Reset the HEAD branch.
     * Delete all files that are not in checkout branch.
     *
     * @param branchName Name of branch to check out
     */
    public static void checkOutBranch(String branchName) {
        Commit branchHeadCommit;
        if (branchName.contains("/")) {
            String[] parts = branchName.split("/", 2);
            String remoteName = parts[0];
            String branchNameRm = parts[1];
            File branchFile = join(REFS_REMOTES_DIR, remoteName, branchNameRm);
            if (!branchFile.exists()) {
                quit("No such branch exists.");
            }
            branchHeadCommit = getCommit(readContentsAsString(branchFile));
        } else {
            File branchFile = join(HEADS_DIR, branchName);
            if (!branchFile.exists()) {
                quit("No such branch exists.");
            }
            branchHeadCommit = getCommit(readContentsAsString(branchFile));
        }
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))) {
            quit("No need to checkout the current branch.");
        }

        // Check if there are untracked files.
        checkUntrackedFiles();

        // Change files.
        writeAllFilesCWD(branchHeadCommit);

        // Reset HEAD branch.
        writeContents(HEAD_FILE, branchName);

        // Delete files that are not in the new commit.
        deleteFilesNotInHEADCommit();

        // Clear the staging area.
        clearStaging();
    }

    /**
     * Create a new branch with the given name.This is actually a pointer pointing at HEAD.
     * Note that HEAD shouldn't change.
     *
     * @param branchName Name of the new branch
     */
    public static void branch(String branchName) {
        File branchFile = join(HEADS_DIR, branchName);
        if (branchFile.exists()) {
            quit("A branch with that name already exists.");
        }
        try {
            branchFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(branchFile, getHeadCommit().getId());
    }

    /**
     * Deletes a branch with the given name.
     * That's to say only deletes the branch pointer, but do not delete those commits.
     *
     * @param branchName Name of branch to delete
     */
    public static void rmBranch(String branchName) {
        File branchToDelete = join(HEADS_DIR, branchName);
        if (!branchToDelete.exists()) {
            quit("A branch with that name does not exist.");
        }
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))) {
            quit("Cannot remove the current branch.");
        }
        branchToDelete.delete();
    }

    /**
     * Set CWD to the destined commit, delete files that aren't tracked by that commit.
     * Move the HEAD pointer.
     * Clear staging area.
     *
     * @param prefix An arbitrary commit
     */
    public static void reset(String prefix) {
        // Get the commit.
        Commit destinedCommit = findCorrespondingCommit(prefix);
        Map<String, String> destinedTrackedFiles = destinedCommit.getTrackedFiles();

        // Check if there's untracked file.
        // Correct untracked file check for reset
        List<String> filesInCWD = plainFilenamesIn(CWD);
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                // If this file is NOT tracked by current HEAD
                if (!getHeadCommit().getTrackedFiles().containsKey(fileInCWD)) {
                    // And if the reset target commit WOULD overwrite this file
                    if (destinedTrackedFiles.containsKey(fileInCWD)) {
                        // Compute hash to check if it’s actually identical
                        File working = join(CWD, fileInCWD);
                        String workingHash = generateHash(working);
                        String targetHash = destinedTrackedFiles.get(fileInCWD);
                        if (!workingHash.equals(targetHash)) {
                            quit("There is an untracked file in the way; "
                                    + "delete it, or add and commit it first.");
                        }
                    }
                }
            }
        }

        // Write files into CWD.
        writeAllFilesCWD(destinedCommit);

        // This part is kind of weird? Change pointer.
        String branchName = readContentsAsString(HEAD_FILE);
        if (branchName.contains("/")) {
            String[] parts = branchName.split("/", 2);
            writeContents(join(REFS_REMOTES_DIR, parts[0], parts[1]), destinedCommit.getId());
        } else {
            writeContents(join(HEADS_DIR, branchName), destinedCommit.getId());
        }

        // Delete files not in current HEAD commit.
        deleteFilesNotInHEADCommit();

        // Clear staging area.
        clearStaging();
    }

    /**
     * Merges files from the given branch into the current branch.
     * 2 special situations below:
     * Situation 1(linear): Given branch's head commit is the split point. 
     *      Do nothing and print message.
     * Situation 2(linear): Current branch's head commit is the split point. 
     *      Checkout the given branch.
     * Both situation above don't make new commit.
     *
     * @param branchName The given branch to merge from
     */
    public static void merge(String branchName) {
        // Check if there are staged files uncommited.
        if (hasFiles(ADD_DIR) || hasFiles(REMOVE_DIR)) {
            quit("You have uncommited changes.");
        }

        // Check if the branch name exists.
        Commit givenBranchCommit = getBranchCommit(branchName);
        if (givenBranchCommit == null) {
            quit("A branch with that name does not exist.");
        }

        // Check if the given branch is the current branch.
        String currentBranchName = readContentsAsString(HEAD_FILE);
        if (Objects.equals(branchName, currentBranchName)) {
            quit("Cannot merge a branch with itself.");
        }

        // Check if there are untracked files.
        checkUntrackedFiles();

        // Get the split commit.
        Commit currentCommit = getHeadCommit();
        Commit splitPointCommit = getSplitPointCommit(currentCommit, givenBranchCommit);

        // Situation 1.
        if (splitPointCommit != null
                && Objects.equals(splitPointCommit.getId(), givenBranchCommit.getId())) {
            quit("Given branch is an ancestor of the current branch.");
        }

        // Situation 2.
        if (splitPointCommit != null
                && Objects.equals(splitPointCommit.getId(), currentCommit.getId())) {
            checkOutBranch(branchName);
            quit("Current branch fast-forwarded.");
        }

        // Non-special cases below.
        mergeOrdinaryCase(branchName);

        mergeFilesInGiven(branchName);

        // Commit all those changes.
        String message = "Merged " + branchName + " into " + currentBranchName + ".";
        commit(message, givenBranchCommit.getId());
    }

    /**
     * Generate log information like a graph to showcase the structure of the tree.
     */
    public static void graphLog() {
        Commit currentCommit = getHeadCommit();
        int haveBranches = 1;
        int onBranch = 0;
        boolean onSecondBranch = false;
        String headBranch = readContentsAsString(HEAD_FILE);
        Map<String, String> branchesLeaves = readFolderToMap(HEADS_DIR);
        File[] remoteFolders = REFS_REMOTES_DIR.listFiles();
        if (remoteFolders != null) {
            Map<String, String> branchesLeavesRm = readFolderToMap(remoteFolders);
            branchesLeaves.putAll(branchesLeavesRm);
        }
        String headId = getHeadCommit().getId();
        Commit splitCommit = null;
        Commit originalParentCommit = null;

        while (true) {
            String currentCommitId = currentCommit.getId();
            for (int i = 0; i < haveBranches; i++) {
                if (i == onBranch) {
                    System.out.print("* ");
                    continue;
                }
                System.out.print("| ");
            }
            System.out.print(currentCommit.getId().substring(0, 7) + " ");
            if (Objects.equals(currentCommitId, headId)) {
                System.out.print("(HEAD -> " + headBranch + ") ");
                System.out.print("(" + branchesLeaves.get(currentCommitId) + ") ");
            } else if (branchesLeaves.containsKey(currentCommitId)) {
                System.out.print("(" + branchesLeaves.get(currentCommitId) + ") ");
            }
            System.out.println(currentCommit.getMessage());

            if (currentCommit.getSecondParent() != null) {
                Commit parentCommit = getCommit(currentCommit.getParent());
                Commit secondParentCommit = getCommit(currentCommit.getSecondParent());
                splitCommit = getSplitPointCommit(parentCommit, secondParentCommit);
                // 打印分支分叉图形并调整行列状态
                for (int i = 0; i < haveBranches; i++) {
                    System.out.print("| ");
                }
                System.out.println("\\");
                haveBranches++;
                onBranch++;
                // 保存回到主分支时要用的 commit（应为当前合并提交的第一父）
                originalParentCommit = parentCommit;
            }

            // 在进入下一次迭代、移动到下一个提交前：如果当前正在遍历第二分支，
            // 且当前提交的父指向 split point，则应打印斜线结束分支并回到主分支轨迹。
            if (!onSecondBranch && splitCommit != null
                    && Objects.equals(currentCommit.getParent(), splitCommit.getId())) {
                haveBranches--;
                for (int i = 0; i < haveBranches; i++) {
                    System.out.print("| ");
                }
                System.out.println("/");
            }
            if (currentCommit.getParent() == null) {
                break;
            }
            // 移动到下一个提交：
            if (currentCommit.getSecondParent() != null) {
                // 先跳到第二父分支开始遍历第二支线
                currentCommit = getCommit(currentCommit.getSecondParent());
                onSecondBranch = true;
                // originalParentCommit 已在上面保存为 parentCommit
            } else if (onSecondBranch && splitCommit != null
                    && Objects.equals(currentCommit.getParent(), splitCommit.getId())) {
                // 到达 split point，回到原先的主分支继续遍历
                onSecondBranch = false;
                onBranch--;
                currentCommit = originalParentCommit;
            } else {
                currentCommit = getCommit(currentCommit.getParent());
            }
        }
    }

    /**
     * Add a directory as a remote repo, so that user can access it by the name.
     *
     * @param remoteName The name of the repo
     * @param remoteDirectory The path of the directory
     */
    public static void addRemote(String remoteName, String remoteDirectory) {
        File remoteFile = join(REMOTE_DIR, remoteName);
        if (remoteFile.exists()) {
            quit("A remote with that name already exists.");
        }
        try {
            remoteFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String normalizedPath = remoteDirectory.replace("/", File.separator);
        writeContents(remoteFile, normalizedPath);

        // Create a directory in REFS_REMOTE_DIR
        File remoteBranchDir = join(REFS_REMOTES_DIR, remoteName);
        if (!remoteBranchDir.exists()) {
            remoteBranchDir.mkdir();
        }
    }

    /**
     * Remove information with the given remote name.
     *
     * @param remoteName The remote to delete
     */
    public static void removeRemote(String remoteName) {
        File removeRemoteFile = join(REMOTE_DIR, remoteName);
        if (!removeRemoteFile.exists()) {
            quit("A remote with that name does not exist.");
        }
        removeRemoteFile.delete();
    }

    /**
     * Append the current branch's commits to the end of the given branch.
     *
     * @param remoteName The remote repo to push to
     * @param remoteBranch The branch of remote repo to append commits to
     */
    public static void push(String remoteName, String remoteBranch) {
        File remoteInfo = join(REMOTE_DIR, remoteName);
        if (!remoteInfo.exists()) {
            quit("A remote with that name does not exist.");
        }
        String remotePath = readContentsAsString(remoteInfo);
        File gitletDirRm = Paths.get(remotePath).toFile();

        if (!(gitletDirRm.exists() && gitletDirRm.isDirectory())) {
            quit("Remote directory not found.");
        }

        File branchRmFile = join(gitletDirRm, "refs", "heads", remoteBranch);
        // If the branch in remote repo does not exist, add the branch.
        if (!branchRmFile.exists()) {
            try {
                branchRmFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Get the head commit of the remote repo.
        File headFileDir = join(gitletDirRm, "HEAD");
        String headBranchRm = readContentsAsString(headFileDir);
        String headCommitIdRm = readContentsAsString(
                join(gitletDirRm, "refs", "heads", headBranchRm));

        // Check if it's in local head's history.
        if (!findCommitIdInHistory(headCommitIdRm)) {
            quit("Please pull down remote changes before pushing.");
        }

        // Get the set of commits to copy to repo.
        String currentCommitId = getHeadCommit().getId();
        Set<String> idsNeedCopying = findCommitsNeedCopying(
                headCommitIdRm, currentCommitId, GITLET_DIR);

        // Copy the commits and blobs to the repo.
        File commitDirRm = join(gitletDirRm, "objects", "commits");
        for (String id : idsNeedCopying) {
            // Write commit.
            File commitFileRm = join(commitDirRm, id);
            File commitFileLc = join(COMMITS_DIR, id);
            if (commitFileRm.exists()) {
                continue;
            }
            try {
                commitFileRm.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeObject(commitFileRm, readObject(commitFileLc, Commit.class));

            // Write blobs.
            Map<String, String> trackedFiles = getCommit(id).getTrackedFiles();
            for (String trackedFile : trackedFiles.keySet()) {
                String blobId = trackedFiles.get(trackedFile);
                File blobFileRm = join(gitletDirRm, "objects", "blobs", blobId);
                File blobFileLc = join(BLOBS_DIR, blobId);

                if (blobFileRm.exists()) {
                    continue;
                }
                try {
                    blobFileRm.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                writeContents(blobFileRm, (Object) readContents(blobFileLc));
            }
        }

        // Change the branch's head commit.
        writeContents(branchRmFile, currentCommitId);
    }

    /**
     * Get all the commits in remote repo to local repo, create a new branch in local,
     * but not merge.
     *
     * @param remoteName The name of remote repo to fetch from
     * @param remoteBranch The branch of remote repo to fetch
     */
    public static void fetch(String remoteName, String remoteBranch) {
        File remoteInfo = join(REMOTE_DIR, remoteName);
        if (!remoteInfo.exists()) {
            quit("A remote with that name does not exist.");
        }
        String remotePath = readContentsAsString(remoteInfo);
        File gitletDirRm = Paths.get(remotePath).toFile();

        if (!(gitletDirRm.exists() && gitletDirRm.isDirectory())) {
            quit("Remote directory not found.");
        }

        File branchRmFile = join(gitletDirRm, "refs", "heads", remoteBranch);
        if (!branchRmFile.exists()) {
            quit("That remote does not have that branch.");
        }

        // Get the set of commits to copy to local repo.
        String headIdLc = getHeadCommit().getId();
        String headIdRm = readContentsAsString(branchRmFile);
        Set<String> idsNeedCopying = findCommitsNeedCopying(headIdLc, headIdRm, gitletDirRm);

        // Copy all files into local repo.
        File commitDirRm = join(gitletDirRm, "objects", "commits");
        for (String id : idsNeedCopying) {
            // Write commit.
            File commitFileRm = join(commitDirRm, id);
            File commitFileLc = join(COMMITS_DIR, id);
            if (commitFileLc.exists()) {
                continue;
            }
            try {
                commitFileLc.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeObject(commitFileLc, readObject(commitFileRm, Commit.class));

            // Write blobs.
            Map<String, String> trackedFiles = getCommit(id, gitletDirRm).getTrackedFiles();
            for (String trackedFile : trackedFiles.keySet()) {
                String blobId = trackedFiles.get(trackedFile);
                File blobFileRm = join(gitletDirRm, "objects", "blobs", blobId);
                File blobFileLc = join(BLOBS_DIR, blobId);

                if (blobFileLc.exists()) {
                    continue;
                }
                try {
                    blobFileRm.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                writeContents(blobFileLc, (Object) readContents(blobFileRm));
            }
        }

        // Create a new branch in local.
        File branchDir = join(REFS_REMOTES_DIR, remoteName);
        File branchFile = join(branchDir, remoteBranch);
        if (!branchFile.exists()) {
            try {
                branchFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        writeContents(branchFile, headIdRm);
    }

    public static void pull(String remoteName, String remoteBranch) {
        checkUntrackedFiles();
        fetch(remoteName, remoteBranch);
        String branchName = remoteName + "/" + remoteBranch;
        merge(branchName);
    }

    /**
     * Get the head commit by getting HEAD id in persistence.
     */
    private static Commit getHeadCommit() {
        String branch = readContentsAsString(HEAD_FILE);
        String headCommitId = null;
        if (branch.contains("/")) {
            String[] parts = branch.split("/", 2);
            String remoteName = parts[0];
            String branchName = parts[1];
            headCommitId = readContentsAsString(join(REFS_REMOTES_DIR, remoteName, branchName));
        } else {
            headCommitId = readContentsAsString(join(HEADS_DIR, branch));
        }
        return readObject(join(COMMITS_DIR, headCommitId), Commit.class);
    }

    /**
     * Get the commit by its id.
     *
     * @param id The id of the commit
     * @return The Commit corresponding to this id
     */
    private static Commit getCommit(String id) {
        return readObject(join(COMMITS_DIR, id), Commit.class);
    }

    /**
     * Get the commit by its id from the given gitletDir.
     *
     * @param id The id of the commit
     * @param gitletDir The directory to find the commit
     * @return The commit corresponding to this id
     */
    private static Commit getCommit(String id, File gitletDir) {
        return readObject(join(gitletDir, "objects", "commits", id), Commit.class);
    }

    /**
     * Print out the log information of one commit.
     *
     * @param currentCommit The commit to print log
     */
    private static void printLog(Commit currentCommit) {
        System.out.println("===");
        System.out.println("commit " + currentCommit.getId());
        if (currentCommit.getSecondParent() != null) {
            System.out.println("Merge: " + currentCommit.getParent().substring(0, 7)
                    + " " + currentCommit.getSecondParent().substring(0, 7));
        }
        System.out.println("Date: " + currentCommit.getTimestamp());
        System.out.println(currentCommit.getMessage());
        System.out.println();
    }

    /**
     * Clear the files in staging area.
     */
    private static void clearStaging() {
        deleteChildren(ADD_DIR);
        deleteChildren(REMOVE_DIR);
    }

    /**
     * Delete children files and directories recursively.
     *
     * @param dir The directory to delete children
     */
    private static void deleteChildren(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteChildren(f);
            }
            if (!f.delete()) {
                throw new RuntimeException("Failed to delete staging file: " + f.getPath());
            }
        }
    }

    /**
     * Generate SHA-1 hash for a file.
     *
     * @param file The file to generate hash
     * @return The hash of the file
     */
    private static String generateHash(File file) {
        return Utils.sha1((Object) readContents(file));
    }

    /**
     * Get the Commit by the prefix.
     *
     * @param prefix The arbitrary commit id
     * @return The wanted commit
     */
    private static Commit findCorrespondingCommit(String prefix) {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        List<String> qualifiedIds = new ArrayList<>();
        assert commitIds != null;
        for (String commitId : commitIds) {
            if (commitId.startsWith(prefix)) {
                qualifiedIds.add(commitId);
            }
        }
        if (qualifiedIds.isEmpty()) {
            quit("No commit with that id exists.");
        } else if (qualifiedIds.size() > 1) {
            quit("Prefix not unique.");
        }
        return getCommit(qualifiedIds.get(0));
    }

    /**
     * Check if there are untracked files exist. If so, print a message.
     */
    private static void checkUntrackedFiles() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        Map<String, String> headTrackedFiles = getHeadCommit().getTrackedFiles();
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                if (headTrackedFiles.containsKey(fileInCWD)) {
                    continue;
                }
                quit("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
            }
        }
    }

    /**
     * Delete the files that are not in current commit.
     */
    private static void deleteFilesNotInHEADCommit() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        Map<String, String> headTrackedFiles = getHeadCommit().getTrackedFiles();
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                if (headTrackedFiles.containsKey(fileInCWD)) {
                    continue;
                }
                restrictedDelete(fileInCWD);
            }
        }
    }

    /**
     * Write everything in one Commit to current directory.
     *
     * @param targetCommit The commit to get files from
     */
    private static void writeAllFilesCWD(Commit targetCommit) {
        Map<String, String> trackedFilesBranch = targetCommit.getTrackedFiles();
        for (String fileName : trackedFilesBranch.keySet()) {
            File fileCurWorkingDir = join(CWD, fileName);
            if (!fileCurWorkingDir.exists()) {
                try {
                    fileCurWorkingDir.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            writeContents(fileCurWorkingDir, (Object) readContents(
                    join(BLOBS_DIR, trackedFilesBranch.get(fileName))));
        }
    }

    /**
     * Check if a directory has files inside.
     *
     * @param directory The directory to check
     * @return True if it has files
     */
    private static boolean hasFiles(File directory) {
        File[] files = directory.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * Quit the program with the message.
     *
     * @param message The message to print
     */
    private static void quit(String message) {
        System.out.println(message);
        System.exit(0);
    }

    /**
     * Get the split point commit.
     *
     * @param c1 The newest commit of one branch
     * @param c2 The newest commit of another branch
     * @return The split point commit; null if no split point
     */
    private static Commit getSplitPointCommit(Commit c1, Commit c2) {
        if (c1 == null || c2 == null) {
            return null;
        }
        // 收集 c1 的所有祖先（包括自身）
        Set<String> ancestors1 = new HashSet<>();
        Deque<Commit> stack = new ArrayDeque<>();
        stack.push(c1);
        while (!stack.isEmpty()) {
            Commit cur = stack.pop();
            String id = cur.getId();
            if (ancestors1.contains(id)) {
                continue;
            }
            ancestors1.add(id);
            if (cur.getParent() != null) {
                stack.push(getCommit(cur.getParent()));
            }
            if (cur.getSecondParent() != null) {
                stack.push(getCommit(cur.getSecondParent()));
            }
        }
        // 从 c2 开始做 BFS，找到第一个出现在 ancestors1 的提交（即最近的公共祖先）
        Deque<Commit> queue = new ArrayDeque<>();
        Set<String> visited2 = new HashSet<>();
        queue.add(c2);
        while (!queue.isEmpty()) {
            Commit cur = queue.remove();
            String id = cur.getId();
            if (visited2.contains(id)) {
                continue;
            }
            visited2.add(id);
            if (ancestors1.contains(id)) {
                return cur;
            }
            if (cur.getParent() != null) {
                queue.add(getCommit(cur.getParent()));
            }
            if (cur.getSecondParent() != null) {
                queue.add(getCommit(cur.getSecondParent()));
            }
        }
        return null;
    }

    /**
     * Deel with merge conflict. Form a file with special content.
     *
     * @param fileName The name of the conflict file
     * @param currentFile The file from current branch, null if not exist
     * @param givenFile The file from given branch, null if not exist
     */
    private static void deelWithConflictMerge(String fileName, File currentFile, File givenFile) {
        System.out.println("Encountered a merge conflict.");

        String currentContent = (currentFile == null) ? "" : readContentsAsString(currentFile);
        String givenContent = (givenFile == null) ? "" : readContentsAsString(givenFile);

        String newContent = "<<<<<<< HEAD\n" + currentContent
                + "=======\n" + givenContent + ">>>>>>>\n";
        File newContentFile = join(CWD, fileName);
        if (!newContentFile.exists()) {
            try {
                newContentFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        writeContents(newContentFile, newContent);
        add(fileName);
    }

    /**
     * Print Modified but not staged files.
     */
    private static void printModifiedNotStagedFiles() {
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> printOutFiles = new ArrayList<>(5);
        List<String> stagedFiles = plainFilenamesIn(ADD_DIR);
        List<String> removedFiles = plainFilenamesIn(REMOVE_DIR);
        // Get the file tracked in the current commit,
        // changed in the working directory, but not staged.
        Commit headCommit = getHeadCommit();
        Map<String, String> headTrackedFiles = headCommit.getTrackedFiles();
        for (String trackedFileName : headTrackedFiles.keySet()) {
            if (stagedFiles != null) {
                if (stagedFiles.contains(trackedFileName)) {
                    continue;
                }
            }
            File fileInWorkingDir = join(CWD, trackedFileName);
            if (fileInWorkingDir.exists()) {
                String currentHash = generateHash(fileInWorkingDir);
                if (!currentHash.equals(headTrackedFiles.get(trackedFileName))) {
                    printOutFiles.add(trackedFileName + " (modified)");
                }
            }
        }
        // Get the file staged for addition,
        // but with different contents than in the working directory;
        // and get the file staged for addition, but deleted in the working directory.
        if (stagedFiles != null) {
            for (String stagedFile : stagedFiles) {
                File fileInWorkingDir = join(CWD, stagedFile);
                if (!fileInWorkingDir.exists()) {
                    printOutFiles.add(stagedFile + " (deleted)");
                }
                String addHashOrigin = readContentsAsString(join(ADD_DIR, stagedFile));
                if (fileInWorkingDir.exists()) {
                    if (!generateHash(fileInWorkingDir).equals(addHashOrigin)) {
                        printOutFiles.add(stagedFile + " (modified)");
                    }
                }
            }
        }
        // Get the file Not staged for removal,
        // but tracked in the current commit and deleted from the working directory.
        for (String trackedFileName : headTrackedFiles.keySet()) {
            if (removedFiles != null) {
                if (removedFiles.contains(trackedFileName)) {
                    continue;
                }
            }
            File fileInWorkingDir = join(CWD, trackedFileName);
            if (!fileInWorkingDir.exists()) {
                printOutFiles.add(trackedFileName + " (deleted)");
            }
        }
        // Print out all those files.
        Collections.sort(printOutFiles);
        for (String printOutFile : printOutFiles) {
            System.out.println(printOutFile);
        }
        System.out.println();
    }

    /**
     * Print out branches, including remote branches, in status.
     */
    private static void printBranches() {
        System.out.println("=== Branches ===");
        List<String> branchNames = plainFilenamesIn(HEADS_DIR);
        if (branchNames == null) {
            branchNames = new ArrayList<>();
        } else {
            branchNames = new ArrayList<>(branchNames);
        }
        File[] remoteFiles = REFS_REMOTES_DIR.listFiles();
        if (remoteFiles != null) {
            for (File remoteFile : remoteFiles) {
                List<String> branchNamesInRmDir = plainFilenamesIn(remoteFile);
                if (branchNamesInRmDir != null) {
                    for (String branchName : branchNamesInRmDir) {
                        branchNames.add(remoteFile.getName() + "/" + branchName);
                    }
                }
            }
        }
        assert branchNames != null;
        Collections.sort(branchNames);
        for (String branchName : branchNames) {
            String currentBranch = readContentsAsString(HEAD_FILE);
            if (Objects.equals(branchName, currentBranch)) {
                System.out.print("*");
            }
            System.out.println(branchName);
        }
        System.out.println();
    }

    /**
     * Read files inside a folder to a map.
     *
     * @param folder The folder to read
     * @return The map with key = file name, value = file content
     */
    private static Map<String, String> readFolderToMap(File folder) {
        Map<String, String> map = new HashMap<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String content = readContentsAsString(file);
                    String name = file.getName();
                    if (map.containsKey(content)) {
                        map.put(content, map.get(content) + ", " + name);
                    } else {
                        map.put(content, name);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Read files inside the folders to a map, prepending folders' name.
     *
     * @param folders The folders to read
     * @return The map with key = file name, value = file content
     */
    private static Map<String, String> readFolderToMap(File[] folders) {
        Map<String, String> map = new HashMap<>();
        for (File folder : folders) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String content = readContentsAsString(file);
                        String name = folder.getName() + "/" + file.getName();
                        if (map.containsKey(content)) {
                            map.put(content, map.get(content) + ", " + name);
                        } else {
                            map.put(content, name);
                        }
                    }
                }
            }
        }
        return map;
    }

    /**
     * Merge in ordinary case.
     *
     * @param branchName The name of the given branch
     */
    private static void mergeOrdinaryCase(String branchName) {
        Commit currentCommit = getHeadCommit();
        Commit givenBranchCommit = getBranchCommit(branchName);
        Commit splitPointCommit = getSplitPointCommit(currentCommit, givenBranchCommit);

        Map<String, String> filesCurrentCommit = currentCommit.getTrackedFiles();
        Map<String, String> filesGivenCommit = givenBranchCommit.getTrackedFiles();
        Map<String, String> filesSplitCommit = splitPointCommit.getTrackedFiles();

        if (filesSplitCommit == null) {
            filesSplitCommit = new HashMap<>();
        }

        for (String fileNameCurrentCommit : filesCurrentCommit.keySet()) {
            if (filesGivenCommit.containsKey(fileNameCurrentCommit)
                    && filesSplitCommit.containsKey(fileNameCurrentCommit)) {
                File fileCWD = join(CWD, fileNameCurrentCommit);
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String givenFileHash = filesGivenCommit.get(fileNameCurrentCommit);
                String splitFileHash = filesSplitCommit.get(fileNameCurrentCommit);

                // Files modified in the given branch but not modified in current branch
                // should be changed into the version in the given branch.
                if (Objects.equals(currentFileHash, splitFileHash)
                        && (!Objects.equals(givenFileHash, splitFileHash))) {
                    File givenFileBlob = join(BLOBS_DIR, givenFileHash);
                    writeContents(fileCWD, (Object) readContents(givenFileBlob));
                    add(fileNameCurrentCommit);
                }

                // Files modified in different ways (3 files exists) are in conflict.
                if ((!Objects.equals(currentFileHash, givenFileHash))
                        && (!Objects.equals(currentFileHash, splitFileHash))
                        && (!Objects.equals(givenFileHash, splitFileHash))) {
                    File currentFileBlob = join(BLOBS_DIR,
                            filesCurrentCommit.get(fileNameCurrentCommit));
                    File givenFileBlob = join(BLOBS_DIR,
                            filesGivenCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, givenFileBlob);
                }
            }

            if ((!filesGivenCommit.containsKey(fileNameCurrentCommit))
                    && filesSplitCommit.containsKey(fileNameCurrentCommit)) {
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String splitFileHash = filesSplitCommit.get(fileNameCurrentCommit);
                // Files unmodified in current branch, but absent in given branch
                // should be removed and untracked.
                if (Objects.equals(currentFileHash, splitFileHash)) {
                    File fileCWD = join(CWD, fileNameCurrentCommit);
                    fileCWD.delete();
                    rm(fileNameCurrentCommit);
                } else {
                    // Files modified in current, deleted in given, should deel with conflict.
                    File currentFileBlob = join(BLOBS_DIR,
                            filesCurrentCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, null);
                }
            }

            // Files absent at split, modified differently in given and current,
            // should deel with conflict.
            if (!filesSplitCommit.containsKey(fileNameCurrentCommit)
                    && filesGivenCommit.containsKey(fileNameCurrentCommit)) {
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String givenFileHash = filesGivenCommit.get(fileNameCurrentCommit);
                if (!Objects.equals(currentFileHash, givenFileHash)) {
                    File currentFileBlob = join(BLOBS_DIR,
                            filesCurrentCommit.get(fileNameCurrentCommit));
                    File givenFileBlob = join(BLOBS_DIR,
                            filesGivenCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, givenFileBlob);
                }
            }
        }
    }

    /**
     * Merge the files from the given branch's leaf commit.
     *
     * @param branchName The name of the given branch
     */
    private static void mergeFilesInGiven(String branchName) {
        Commit currentCommit = getHeadCommit();
        Commit givenBranchCommit = getBranchCommit(branchName);
        Commit splitPointCommit = getSplitPointCommit(currentCommit, givenBranchCommit);

        Map<String, String> filesCurrentCommit = currentCommit.getTrackedFiles();
        Map<String, String> filesGivenCommit = givenBranchCommit.getTrackedFiles();
        Map<String, String> filesSplitCommit = splitPointCommit.getTrackedFiles();
        for (String fileNameGivenCommit : filesGivenCommit.keySet()) {
            // Files only present in the given branch should be checked out and staged.
            if ((!filesCurrentCommit.containsKey(fileNameGivenCommit))
                    && (!filesSplitCommit.containsKey(fileNameGivenCommit))) {
                checkOutCommit(givenBranchCommit.getId(), fileNameGivenCommit);
                add(fileNameGivenCommit);
            }
            // Files modified in given, deleted in current, should deel with conflict.
            if ((!filesCurrentCommit.containsKey(fileNameGivenCommit))
                    && filesSplitCommit.containsKey(fileNameGivenCommit)) {
                String givenFileHash = filesGivenCommit.get(fileNameGivenCommit);
                String splitFileHash = filesSplitCommit.get(fileNameGivenCommit);
                if (!Objects.equals(givenFileHash, splitFileHash)) {
                    File givenFileBlob = join(BLOBS_DIR,
                            filesGivenCommit.get(fileNameGivenCommit));
                    deelWithConflictMerge(fileNameGivenCommit, null, givenFileBlob);
                }
            }
        }
    }

    /**
     * Find the commit id in local head's history, covering parents and second parents.
     *
     * @param findId The id to find
     * @return True if found
     */
    private static boolean findCommitIdInHistory(String findId) {
        String headCommitId = getHeadCommit().getId();
        Stack<String> commitIds = new Stack<>();
        commitIds.push(headCommitId);
        while (!commitIds.isEmpty()) {
            String currentId = commitIds.pop();
            if (getCommit(currentId).getParent() != null) {
                String parentId = getCommit(currentId).getParent();
                if (Objects.equals(parentId, findId)) {
                    return true;
                } else {
                    commitIds.push(parentId);
                }
            }
            if (getCommit(currentId).getSecondParent() != null) {
                String secondParentId = getCommit(currentId).getSecondParent();
                if (Objects.equals(secondParentId, findId)) {
                    return true;
                } else {
                    commitIds.push(secondParentId);
                }
            }
        }
        return false;
    }

    /**
     * Find the ids of the commits that need to get copied to remote repo.
     *
     * @param headId The head commit in remote repo
     * @return The set of commit ids
     */
    private static Set<String> findCommitsNeedCopying(
            String headId, String currentCommitId, File gitletDir) {
        Set<String> idSet = new HashSet<>();
        Stack<String> idStack = new Stack<>();
        Set<String> visited = new HashSet<>();
        idStack.push(currentCommitId);
        while (!idStack.isEmpty()) {
            String currentId = idStack.pop();
            if (visited.contains(currentId)) {
                continue;
            }
            visited.add(currentId);

            if (Objects.equals(currentId, headId)) {
                continue;
            }
            idSet.add(currentId);

            if (getCommit(currentId, gitletDir).getParent() != null) {
                String parentId = getCommit(currentId, gitletDir).getParent();
                idStack.push(parentId);
            }
            if (getCommit(currentId, gitletDir).getSecondParent() != null) {
                String secondParentId = getCommit(currentId, gitletDir).getSecondParent();
                idStack.push(secondParentId);
            }
        }
        return idSet;
    }

    /**
     * Get the head commit of the branch, considering the REF_REMOTES_DIR.
     *
     * @param branchName The name of branch, may include "/"
     * @return The head commit of the branch, null if it doesn't exist
     */
    private static Commit getBranchCommit(String branchName) {
        Commit returnCommit;
        if (branchName.contains("/")) {
            String[] parts = branchName.split("/", 2);
            File branchFile = join(REFS_REMOTES_DIR, parts[0], parts[1]);
            if (!branchFile.exists()) {
                return null;
            }
            String id = readContentsAsString(branchFile);
            returnCommit = getCommit(id);
        } else {
            File branchFile = join(HEADS_DIR, branchName);
            if (!branchFile.exists()) {
                return null;
            }
            String id = readContentsAsString(branchFile);
            returnCommit = getCommit(id);
        }
        return returnCommit;
    }
}
