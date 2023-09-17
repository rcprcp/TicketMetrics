package com.cottagecoders.ticketmetrics;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Audit;
import org.zendesk.client.v2.model.CustomFieldValue;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.events.Event;
import org.zendesk.client.v2.model.events.NotificationEvent;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicketMetrics {

  @Parameter(names = {"-s", "--start"}, description = "Start date (oldest) yyyy-MM-dd format. Dates are inclusive.")
  private String start = "";
  @Parameter(names = {"-e", "--end"}, description = "End date (most recent). yyyy-MM-dd format. Dates are inclusive.")
  private String end = "";

  public static void main(String[] args) {
    TicketMetrics tm = new TicketMetrics();
    tm.run(args);
    System.exit(0);

  }

  private void run(String[] args) {
    // process command line args.
    JCommander.newBuilder().addObject(this).build().parse(args);

    // process command line dates..
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    Date startDate = null;
    Date endDate = null;
    try {
      startDate = sdf.parse(start);
      endDate = sdf.parse(end);
      System.out.println("Dates: " + sdf.format(startDate) + "  and " + sdf.format(endDate));

    } catch (ParseException ex) {
      System.out.println("Parse exception ");
      System.exit(6);
    }

    Zendesk zd = null;
    try {
      // set up Zendesk connection
      zd = new Zendesk.Builder(System.getenv("ZENDESK_URL")).setUsername(System.getenv("ZENDESK_EMAIL")).setToken(System.getenv("ZENDESK_TOKEN")).build();

    } catch (Exception ex) {
      System.out.println("Exception: " + ex.getMessage());
      ex.printStackTrace();
      System.exit(1);
    }


    Map<Long, Summary> userCounts = new HashMap<>();
    int ticketCount = 0;
    int closedTickets = 0;


    // we do not use Zendesk search; we do not know up fron how many tickets will be returned:
    // we have a warning from this page: https://support.zendesk.com/hc/en-us/articles/4408886879258#topic_ghr_wsc_3v
    // "Also, search returns only the first 1,000 results even if there are more results."
    // in testing, we get HTTP/422 when querying more than 1000 tickets.

    // Use this code:
    // String searchTerm = String.format("created>%s created<%s", sdf.format(startDate), sdf.format(endDate));
    // System.out.println("searchTerm: " + searchTerm);

    // then iterate on: zd.getTicketsFromSearch(searchTerm)

    for (Ticket t : zd.getTickets()) {
      // backwards date logic, because we want to include the start and end dates.
      // eg. there is only < or > no <= >=    :)

      // ticket are not in date order, must process all tickets.
      // also note that if we search with the searchTerm, this date-checking code is not needed
      // (and the dates for the search term are NOT inclusive.)
      if (t.getCreatedAt().before(startDate) || t.getCreatedAt().after(endDate)) {
        continue;
      }

      if (!t.getStatus().equals(Status.CLOSED) && !t.getStatus().equals(Status.SOLVED)) {
        continue;
      }
      // add additional criteria can be added here...

      ++ticketCount;  //number of tickets processed.

      //no one is assigned to this ticket.
      if (t.getAssigneeId() == null) {
        System.out.println("no assignee for ticket " + t.getId());
        continue;
      }

      // is this assignee in the Map?
      if (userCounts.get(t.getAssigneeId()) == null) {
        // nope, add them.
        userCounts.put(t.getAssigneeId(), new Summary(1, 0, zd.getUser(t.getAssigneeId()).getName()));

      } else {
        // yes, assignee exists in the map.  increment this assignee's ticket count.
        userCounts.get(t.getAssigneeId()).incrementTicketCount();
      }

      // NOTE: to access the events, you need to follow: a ticket has multiple audits,
      // audits have multiple events, and we're going to look for a NotificationEvent with
      // the email text in the event body.
      Iterable<Audit> audits = zd.getTicketAudits(t.getId());
      for (Audit a : audits) {
        List<Event> events = a.getEvents();
        for (Event e : events) {
          if (e instanceof NotificationEvent) {

            // this text appears in the notification:
            if (((NotificationEvent) e).getBody().contains("This request will now be closed")) {

              if (t.getAssigneeId() != null) {
                // we will not worry about auto close tickets if the user has no "regular" tickets.
                Summary sum = userCounts.get(t.getAssigneeId()); // fetch
                if (sum == null) {  // assignee was not found.
                  continue;
                }
                userCounts.get(t.getAssigneeId()).incrementAutoClosedTicketCount();
                ++closedTickets;
              }
            }
          }
        }
      }
    }  // end of ticket gathering and proceesing.

    int pct = closedTickets *100 / ticketCount;
    System.out.println(ticketCount + " tickets processed.  autoclosed: " + closedTickets + " " + pct + "%");
    List<Summary> itemList = new ArrayList<>(userCounts.values());

    // print alphabetically:
    Collections.sort(itemList, new Comparator<Summary>() {
      public int compare(Summary left, Summary right) {
        // case insensitive sort.
        return left.getName().toUpperCase().compareTo(right.getName().toUpperCase());
      }
    });
    System.out.println("\n\nSort by name");
    printThem(itemList);

    // print by ticket count, ascending:
    Collections.sort(itemList, new Comparator<Summary>() {
      public int compare(Summary left, Summary right) {
        // integer sorting.
        return left.getTicketCount().compareTo(right.getTicketCount());
      }
    });
    System.out.println("\n\nSort by ticket count");
    printThem(itemList);

  }

  private void printThem(List<Summary> items) {
    for (Summary s : items) {
      int pct = s.getAutoCloseCount() * 100 / s.getTicketCount();  // to be in the list, someone has at least one ticket
      System.out.println(String.format("%-30s  %d  %d   %d%%",
              s.getName(), s.getTicketCount(), s.getAutoCloseCount(), pct));
    }
  }
}

class Summary {
  private int ticketCount;
  private int autoCloseCount;
  private String name;

  Summary(Integer ticketCount, Integer autoCloseCount, String name) {
    this.ticketCount = ticketCount;
    this.autoCloseCount = autoCloseCount;
    this.name = name;
  }

  public Integer getTicketCount() {
    return ticketCount;
  }

  public void incrementTicketCount() {
    this.ticketCount += 1;
  }
  public void incrementAutoClosedTicketCount() {
    this.autoCloseCount += 1;
  }

  public Integer getAutoCloseCount() {
    return autoCloseCount;
  }

  public String getName() {
    return name;
  }
}
