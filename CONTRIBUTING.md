# Keycloak Cassandra Community

Keycloak Cassandra is a datastore and caching extension for Keycloak, the Open Source Identity and Access Management solution for modern Applications and Services.

## Building and working with the codebase

To build the codebase you need an installed JDK of at least version 17 and Maven.
The tests use the Testcontainers-framework to start a local Apache Cassandra database instance. For this to work, you need Docker installed on your system as well.

## Contributing to Keycloak Cassandra

Keycloak Cassandra welcomes contributions as well as feedback from the community. We do have a few guidelines in place to help you be successful with your contribution to Keycloak Cassandra.

Firstly, if you want to contribute a larger change we ask that you open a 
discussion first. For minor changes you can skip this part and go straight ahead to sending a contribution. Bear in mind that if you open a discussion first you can identify if the change will be accepted, as well as getting early feedback.  

Here's a quick checklist for a good PR, more details below:

1. A discussion around the change
2. A GitHub Issue with a good description associated with the PR
3. One feature/change per PR
4. One commit per PR
5. PR rebased on main (`git rebase`, not `git pull`) 
5. [Good descriptive commit message, with link to issue](#commit-messages-and-issue-linking)
6. No changes to code not directly related to your PR
7. Includes test case to demonstrate your change works
8. Includes documentation

Once you have submitted your PR please monitor it for comments/feedback. We reserve the right to close inactive PRs if
you do not respond within 2 weeks (bear in mind you can always open a new PR if it is closed due to inactivity).

### Submitting your PR

When preparing your PR make sure you have a single commit and your branch is rebased on the main branch from the 
project repository.

This means use the `git rebase` command and not `git pull` when integrating changes from main to your branch. See
[Git Documentation](https://git-scm.com/book/en/v2/Git-Branching-Rebasing) for more details.

We require that you squash to a single commit. You can do this with the `git rebase -i HEAD~X` command where X
is the number of commits you want to squash. See the [Git Documentation](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History)
for more details.

The above helps us review your PR and also makes it easier for us to maintain the repository. It is also required by
our automatic merging process. 

Please, also provide a good description [commit message, with a link to the issue](#commit-messages-and-issue-linking).
We also require that the commit message includes a link to the issue ([linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue)).

### Developer's Certificate of Origin

Any contributions to Keycloak Cassandra must only contain code that can legally be contributed to Keycloak, and which the Keycloak
project can distribute under its license.

Prior to contributing to Keycloak Cassandra please read the [Developer's Certificate of Origin](https://developercertificate.org/)
and sign-off all commits with the `--signoff` option provided by `git commit`. For example:

```
git commit --signoff --message "This is the commit message"
```

This option adds a `Signed-off-by` trailer at the end of the commit log message.

### Commit messages and issue linking

The format for a commit message should look like:

```
A brief descriptive summary

Optionally, more details around how it was implemented

Closes #1234
``` 

The very last part of the commit message should be a link to the GitHub issue, when done correctly GitHub will automatically link the issue with the PR. There are 3 alternatives provided by GitHub here:

* Closes: Issues in the same repository
* Fixes: Issues in a different repository (this shouldn't be used, as issues should be created in the correct repository instead)
* Resolves: When multiple issues are resolved (this should be avoided)

Although, GitHub allows alternatives (close, closed, fix, fixed), please only use the above formats.

Creating multi line commit messages with `git` can be done with:

```
git commit -m "Summary" -m "Optional description" -m "Closes #1234"
```

Alternatively, `shift + enter` can be used to add line breaks:

```
$ git commit -m "Summary
> 
> Optional description
> 
> Closes #1234"
```

For more information linking PRs to issues refer to the [GitHub Documentation](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue).
