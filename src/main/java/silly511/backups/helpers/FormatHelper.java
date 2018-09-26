package silly511.backups.helpers;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.resources.I18n;

public final class FormatHelper {
	
	public static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a");
	public static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm:ss a");
	
	public static final DecimalFormat singleDecimal = new DecimalFormat("#.#");
	
	public static String shortenNumber(long number, long base) {
		long base2 = base * base;
		long base3 = base2 * base;
		long base4 = base3 * base;
		long base5 = base4 * base;
		long base6 = base5 * base;
		
		if (number >= base6)
			return singleDecimal.format((double) number / base6) + "E";
		else if (number >= base5)
			return singleDecimal.format((double) number / base5) + "P";
		else if (number >= base4)
			return singleDecimal.format((double) number / base4) + "T";
		else if (number >= base3)
			return singleDecimal.format((double) number / base3) + "G";
		else if (number >= base2)
			return singleDecimal.format((double) number / base2) + "M";
		else if (number >= base)
			return singleDecimal.format((double) number / base) + "K";
		else
			return String.valueOf(number);
	}
	
	public static String relativeDateFormat(LocalDate date) {
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		
		if (date.equals(today))
			return I18n.format("backups.misc.today");
		else if (date.equals(today.minusDays(1)))
			return I18n.format("backups.misc.yesterday");
		else if (today.toEpochDay() - date.toEpochDay() < 7)
			return I18n.format("backups.misc.weekday." + date.getDayOfWeek().toString().toLowerCase());
		else
			return date.getDayOfMonth() + ", " + I18n.format("backups.misc.month." + date.getMonth().toString().toLowerCase()) + ", " + date.getYear();
	}
	
	public static String relativeDateTimeFormat(ZonedDateTime time) {
		long secondsAgo = Instant.now().getEpochSecond() - time.toEpochSecond();
		
		if (secondsAgo < 60)
			return I18n.format("backups.misc.secondsAgo", secondsAgo);
		else if (secondsAgo < 60 * 60)
			return I18n.format("backups.misc.minutesAgo", secondsAgo / 60);
		else
			return relativeDateFormat(time.toLocalDate()) + " " + time.format(timeFormat);
	}
	
	public static String relativeTimeAgo(Instant time) {
		long sec = Instant.now().getEpochSecond() - time.getEpochSecond();
		List<String> list = new LinkedList<>();
		long t = 0;
		
		if ((t = sec / 31536000) != 0)
			list.add(t + " " + I18n.format("backups.misc.timeunit.year" + (t > 1 ? "s" : "")));
		if ((t = (sec % 31536000) / 86400) != 0)
			list.add(t + " " + I18n.format("backups.misc.timeunit.day" + (t > 1 ? "s" : "")));
		if ((t = (sec % 86400) / 3600) != 0)
			list.add(t + " " + I18n.format("backups.misc.timeunit.hour" + (t > 1 ? "s" : "")));
		if ((t = (sec % 3600) / 60) != 0)
			list.add(t + " " + I18n.format("backups.misc.timeunit.minute" + (t > 1 ? "s" : "")));
		if ((t = sec % 60) != 0)
			list.add(t + " " + I18n.format("backups.misc.timeunit.second" + (t > 1 ? "s" : "")));
		
		return String.join(", ", list);
	}
	
	public static String removeEnd(String str, String remove) {
		return str.endsWith(remove) ? str.substring(0, str.length() - remove.length()) : str;
	}

}
