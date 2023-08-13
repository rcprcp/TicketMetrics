# Ticket Metrics

This program requests tickets within the specified date range
from Zendesk and creates a summary of the number of CLOSED and SOLVED
tickets for each agent over the specified date range.

### Developer notes  
``` shell
git clone http://github.com/rcprcp/TicketMetrics.git
cd TicketMetrics
mvn clean package`
java -jar target/TicketMetrics-1.0-SNAPSHOT-jar-with-dependencies.jar --start "2023-05-01" --end "2023-08-03"
```
To run this, you will need environment variable for the Zendesk URL, Zendesk email and token: 

```shell
export ZENDESK_EMAIL=someone@somewhere.com
export ZENDESK_TOKEN=F4dRblahblahblahnx2DSn
export ZENDESK_URL=https://subdomain.zendesk.com
```
Run the command with a command line switches:

```shell
java -jar target/TicketMetrics-1.0-SNAPSHOT-jar-with-dependencies.jar --start "2023-01-01" --end "2023-07-31"
```