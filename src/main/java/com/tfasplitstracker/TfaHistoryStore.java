package com.tfasplitstracker;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class TfaHistoryStore
{
	private final Path directory;
	private final Gson gson;

	TfaHistoryStore(Path directory, Gson gson)
	{
		this.directory = directory;
		this.gson = gson;
	}

	TfaTracker load(String profile) throws IOException
	{
		String json;
		try
		{
			json = Files.readString(file(profile));
		}
		catch (NoSuchFileException ex)
		{
			return null;
		}
		return decode(json);
	}

	TfaTracker decode(String json)
	{
		JsonObject data = gson.fromJson(json, JsonObject.class);
		if (data == null || !data.has("raids") || !data.get("raids").isJsonArray())
		{
			throw new JsonSyntaxException("Missing TFA raid history");
		}
		TfaTracker tracker = gson.fromJson(data, TfaTracker.class);
		if (tracker.processed == null || tracker.raids.stream().anyMatch(raid -> raid == null
			|| raid.id == null || raid.splits == null || raid.members == null))
		{
			throw new JsonSyntaxException("Incomplete TFA raid history");
		}
		return tracker;
	}

	void save(String profile, TfaTracker tracker) throws IOException
	{
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, "history-", ".tmp");
		try
		{
			Files.writeString(temporary, gson.toJson(tracker));
			Files.move(temporary, file(profile), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	private Path file(String profile)
	{
		String name = BaseEncoding.base16().lowerCase().encode(profile.getBytes(StandardCharsets.UTF_8));
		return directory.resolve(name + ".json");
	}
}
