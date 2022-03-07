# me.untethr.nostr-desk


* majorly work-in-progress
  - i.e., don't file bugs yet
  - expect performance weirdness etc
  - enjoy the ugly
* db schema is subject to change rapidly
  - so MAY NEED TO do this whenever you pull new commits:
    - `$ rm ~/.nostr-desk` 

Pre-requisites:

```
$ brew install clojure/tools/clojure
```

May need to install some java/jdk, say v. 17, if you don't have one

How to run:

```
$ make run
```

### TODO
* seen-on relays w/ popup
  * popup queries db (w/ cache) for seen-on?
* manage followers ux
* design scheme for efficient load of <missing:xx> messages
  * ie messages from contacts you don't follow
* do not re-query everything when re-connecting to a relay
  * bonus: limit requery when contact lists change
* limit timelines by cardinality
  * with "load more" support
* configure/prune logs
  * capture logs in ui admin console
* more nips
* more pretty
  * themes?
* win/mac/linux installables
* keyboard ux
  * ctrl-enter publish/reply
  * esc cancel publish/reply (lose focus)
  * j/k navigate posts