package com.tfasplitstracker;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

final class TfaHistoryStore
{
	private final Filepath directory;
	private final Gson gson;

	TfaHistoryStore(Filepath directory, Gson gson)
	{
		this.directory = directory;
		this.gson = gson;
	}

	TfaTracker load(String profile) throws IOException
	{
		String json;
		try (var input = file(profile).openInputStream())
		{
			json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readAllBytes())).toString();
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
		directory.createDirectories();
		Filepath temporary = directory.createTempFile("history-", ".tmp");
		try
		{
			temporary.write(gson.toJson(tracker));
			temporary.moveTo(file(profile), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		finally
		{
			temporary.deleteIfExists();
		}
	}

	private Filepath file(String profile)
	{
		String name = BaseEncoding.base16().lowerCase().encode(profile.getBytes(StandardCharsets.UTF_8));
		return directory.joinSegment(name + ".json");
	}
}
