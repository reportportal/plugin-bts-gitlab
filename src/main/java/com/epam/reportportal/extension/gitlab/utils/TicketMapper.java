/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.extension.gitlab.utils;

import com.epam.reportportal.extension.gitlab.dto.IssueDto;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class TicketMapper {

  public static Ticket toTicket(IssueDto input) {
    Ticket ticket = new Ticket();
    ticket.setId(input.getIid().toString());
    ticket.setSummary(input.getTitle());
    ticket.setStatus(input.getState());
    ticket.setTicketUrl(input.getWebUrl());
    return ticket;
  }
}
