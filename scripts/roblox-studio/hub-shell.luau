--[[
	Forest hub shell: the sealed box the forest is built inside.

	Paste into Studio's command bar with the hub place open. Re-running it makes a
	second ForestHub, so delete the old one first.

	Everything but the floor is invisible and non-shadowing: the box is collision
	only. What the player sees at the edge is fog, not geometry, so FogEnd has to
	close well inside W and D or the boundary shows.
]]

local W, D, H, T = 300, 300, 80, 2  -- width, depth, height, wall thickness

local hub = Instance.new("Model"); hub.Name = "ForestHub"; hub.Parent = workspace
local function box(name, size, pos)
	local p = Instance.new("Part")
	p.Name, p.Anchored, p.Size, p.Position = name, true, size, pos
	p.Material, p.Color = Enum.Material.Slate, Color3.fromRGB(70, 70, 75)
	p.TopSurface, p.BottomSurface = Enum.SurfaceType.Smooth, Enum.SurfaceType.Smooth
	p.Parent = hub
	return p
end

box("Floor",   Vector3.new(W, 1, D),     Vector3.new(0, 0, 0))
box("Ceiling", Vector3.new(W, 1, D),     Vector3.new(0, H, 0))
box("WallN",   Vector3.new(W, H, T),     Vector3.new(0, H/2, -D/2))
box("WallS",   Vector3.new(W, H, T),     Vector3.new(0, H/2, D/2))
box("WallE",   Vector3.new(T, H, D),     Vector3.new( W/2, H/2, 0))
box("WallW",   Vector3.new(T, H, D),     Vector3.new(-W/2, H/2, 0))

for _, part in ipairs(hub:GetChildren()) do
	if part.Name ~= "Floor" then
		part.Transparency = 1     -- invisible
		part.CastShadow = false   -- and casts no shadow into the scene
		part.Locked = true        -- so you don't click walls while placing trees
	end
end
