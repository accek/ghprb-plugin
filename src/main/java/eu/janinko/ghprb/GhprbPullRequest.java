/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.janinko.ghprb;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

/**
 *
 * @author jbrazdil
 */
public class GhprbPullRequest{
	private int id;
	private Date updated;
	private String head;
	private String target;
	private String author;
	
	private boolean shouldRun = true;
	private boolean askedForApproval = false;
	private boolean mergeable;
	private int running; // TODO remove
	
	private transient GhprbRepo repo;

	GhprbPullRequest(GHPullRequest pr) {
		id = pr.getNumber();
		updated = new Date(0);
		head = pr.getHead().getSha();
		author = pr.getUser().getLogin();
		System.out.println("Created pull request #" + id + " by " + author + " udpdated at: " + updated + " sha: " + head);
		target = pr.getBase().getRef();
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof GhprbPullRequest)) return false;
		
		
		GhprbPullRequest o = (GhprbPullRequest) obj;
		return o.id == id;
	}
	
	public void check(GHPullRequest pr, GhprbRepo ghprbRepo) throws IOException {
		repo = ghprbRepo;
		if(updated.compareTo(pr.getUpdatedAt()) < 0){
			System.out.println("Pull request builder: pr #" + id + " Updated " + pr.getUpdatedAt());
			List<GHIssueComment> comments = pr.getComments();
			for(GHIssueComment comment : comments){
				if(updated.compareTo(comment.getUpdatedAt()) < 0){
					checkComment(comment);
				}
			}
			if(!head.equals(pr.getHead().getSha())){
				head = pr.getHead().getSha();
				shouldRun = true;
			}
			updated = pr.getUpdatedAt();
		}
		if(shouldRun){
			mergeable = pr.getDetailedPullRequest().getMergeable();
			build();
		}
	}

	private void build() throws IOException {
		shouldRun = false;
		if(!repo.isWhitelisted(author)){
			System.out.println("Author of #" + id + " " + author + " not in whitelist!");
			if(!askedForApproval){
				addComment("Can one of the admins verify this patch?");
				askedForApproval = true;
			}
			return;
		}
		StringBuilder sb = new StringBuilder();
		if(repo.cancelBuild(id)){
			sb.append("Previous build stopped. ");
		}
		sb.append("Triggering build using a head: ").append(head);
		repo.startJob(id,head);
		if(mergeable){
			sb.append("\nAnd triggering build using a github automerge");
			repo.startMergeJob(id);
		}
		addComment(sb.toString());
		System.out.println("Pull request builder: " + sb.toString());
		running = 3;
	}
	
	private void addComment(String comment) throws IOException{
		repo.addComment(id,comment);
	}

	private void checkComment(GHIssueComment comment) throws IOException {
		if (repo.isMe(comment.getUser().getLogin())){
			return;
		}
		if (   repo.isWhitelistPhrase(comment.getBody())
		   &&  repo.isAdmin(comment.getUser().getLogin())
		   && !repo.isWhitelisted(author)) {
			repo.addWhitelist(author);
			shouldRun = true;
		}
		if (repo.isRetestPhrase(comment.getBody()) && repo.isWhitelisted(comment.getUser().getLogin())) {
			shouldRun = true;
		}
	}
	
	
	
}
