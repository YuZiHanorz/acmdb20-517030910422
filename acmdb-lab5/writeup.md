# Writeup for Lab 5

# 517030910422 Zihan Yu

## Design decisions
The locking granularity is page by creating a ***PLock*** for every page. I then maintain a ***pLockMap*** which keep track of all ***pLock*** with ***PageId*** and a ***tLockMap*** which keep track of all the pages that is locked by some ***TransactionId***. 

The dead lock is detected by finding if there is a cycle in the dependency graph. Concretely, I maintain a ***dGraph*** where there is an edge from $t_1$ to $t_2$ if $t_1$ request a lock for a page that $t_2$ already has a lock for. Then every time ***getPage*** is called by some $t$, the ***dGraph*** will be updated by adding edges and then I will find if there is a cycle in the graph starting at $t$, if so, a ***TransactionAbortedException*** is thrown. 

## API changes
No API changes.

## Missing or incomplete element
No missing or incomplete element

## time spent and difficult/confusing parts
I finish within two days. It is really annoying to debug, especially for BTreeTest.